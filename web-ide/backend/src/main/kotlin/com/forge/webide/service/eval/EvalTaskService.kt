package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.webide.entity.EvalTaskEntity
import com.forge.webide.repository.EvalTaskRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class EvalTaskService(
    private val evalTaskRepository: EvalTaskRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EvalTaskService::class.java)
    private val yamlMapper = ObjectMapper(YAMLFactory()).registerModule(KotlinModule.Builder().build())

    data class EvalTaskDto(
        val id: String?,
        val name: String,
        val description: String?,
        val input: String,
        val successCriteria: String,
        val graderConfig: String?,
        val taskType: String?,
        val skillTags: List<String>?,
        val difficulty: String,
        val source: String,
        val orgId: String?,
        val isActive: Boolean
    )

    data class YamlEvalSet(
        val id: String?,
        val name: String?,
        val description: String?,
        val prompt: String?,
        val tags: List<String>?,
        val assertions: List<Map<String, Any>>?,
        val quality_criteria: Map<String, Double>?,
        val baseline_pass_rate: Double?
    )

    data class ImportResult(
        val imported: Int,
        val skipped: Int,
        val taskIds: List<String>
    )

    fun listTasks(orgId: String?, taskType: String?, difficulty: String?): List<EvalTaskDto> {
        val tasks = if (orgId != null) {
            when {
                taskType != null -> evalTaskRepository.findAvailableForOrgByType(orgId, taskType)
                difficulty != null -> evalTaskRepository.findAvailableForOrgByDifficulty(orgId, difficulty)
                else -> evalTaskRepository.findAvailableForOrg(orgId)
            }
        } else {
            evalTaskRepository.findByIsActiveTrue()
        }
        return tasks.map { it.toDto() }
    }

    fun getTask(id: String): EvalTaskDto? =
        evalTaskRepository.findById(id).map { it.toDto() }.orElse(null)

    @Transactional
    fun createTask(dto: EvalTaskDto): EvalTaskDto {
        val entity = EvalTaskEntity(
            id = UUID.randomUUID().toString(),
            name = dto.name,
            description = dto.description,
            input = dto.input,
            successCriteria = dto.successCriteria,
            graderConfig = dto.graderConfig,
            taskType = dto.taskType,
            skillTags = dto.skillTags?.let { objectMapper.writeValueAsString(it) },
            difficulty = dto.difficulty,
            source = dto.source,
            orgId = dto.orgId,
            isActive = dto.isActive
        )
        return evalTaskRepository.save(entity).toDto()
    }

    @Transactional
    fun updateTask(id: String, dto: EvalTaskDto): EvalTaskDto? {
        val existing = evalTaskRepository.findById(id).orElse(null) ?: return null
        val updated = EvalTaskEntity(
            id = existing.id,
            name = dto.name,
            description = dto.description,
            input = dto.input,
            successCriteria = dto.successCriteria,
            graderConfig = dto.graderConfig,
            taskType = dto.taskType,
            skillTags = dto.skillTags?.let { objectMapper.writeValueAsString(it) },
            difficulty = dto.difficulty,
            source = dto.source,
            orgId = existing.orgId,
            isActive = dto.isActive,
            createdAt = existing.createdAt
        )
        return evalTaskRepository.save(updated).toDto()
    }

    @Transactional
    fun deleteTask(id: String): Boolean {
        val entity = evalTaskRepository.findById(id).orElse(null) ?: return false
        entity.isActive = false
        evalTaskRepository.save(entity)
        return true
    }

    @Transactional
    fun importFromYaml(yamlContent: String, orgId: String?): ImportResult {
        val evalSet: YamlEvalSet = yamlMapper.readValue(yamlContent)
        val taskId = UUID.randomUUID().toString()

        // Build success_criteria from assertions
        val criteria = buildSuccessCriteria(evalSet)
        val input = evalSet.prompt ?: "No prompt provided"
        val name = evalSet.name ?: evalSet.id ?: "Imported Task"

        // Build grader config from assertions (code-based) + quality_criteria (model-based)
        val graderConfig = buildGraderConfig(evalSet)

        val entity = EvalTaskEntity(
            id = taskId,
            name = name,
            description = evalSet.description,
            input = input,
            successCriteria = criteria,
            graderConfig = graderConfig,
            taskType = "SDLC",
            skillTags = evalSet.tags?.let { objectMapper.writeValueAsString(it) },
            difficulty = inferDifficulty(evalSet),
            source = "YAML_IMPORT",
            orgId = orgId,
            isActive = true
        )

        evalTaskRepository.save(entity)
        log.info("Imported eval task from YAML: {} → {}", name, taskId)
        return ImportResult(imported = 1, skipped = 0, taskIds = listOf(taskId))
    }

    @Transactional
    fun bulkImportFromDirectory(yamlContents: List<String>, orgId: String?): ImportResult {
        var imported = 0
        var skipped = 0
        val taskIds = mutableListOf<String>()

        for (yaml in yamlContents) {
            try {
                val result = importFromYaml(yaml, orgId)
                imported += result.imported
                taskIds.addAll(result.taskIds)
            } catch (e: Exception) {
                log.warn("Failed to import YAML task: {}", e.message)
                skipped++
            }
        }
        return ImportResult(imported = imported, skipped = skipped, taskIds = taskIds)
    }

    private fun buildSuccessCriteria(evalSet: YamlEvalSet): String {
        val assertions = evalSet.assertions ?: return "Task completed successfully"
        return assertions.joinToString("\n") { assertion ->
            val type = assertion["type"] as? String ?: "contains"
            val expected = assertion["expected"] as? String ?: ""
            val description = assertion["description"] as? String ?: ""
            "$type:$expected — $description"
        }
    }

    private fun buildGraderConfig(evalSet: YamlEvalSet): String {
        val hasAssertions = !evalSet.assertions.isNullOrEmpty()
        val hasQuality = !evalSet.quality_criteria.isNullOrEmpty()
        val rubric = evalSet.quality_criteria?.entries?.joinToString("; ") { "${it.key}: ${it.value}" } ?: ""
        val config = mapOf(
            "codeGrader" to hasAssertions,
            "modelGrader" to hasQuality,
            "rubric" to rubric,
            "assertions" to (evalSet.assertions ?: emptyList<Any>()),
            "baselinePassRate" to (evalSet.baseline_pass_rate ?: 0.8)
        )
        return objectMapper.writeValueAsString(config)
    }

    private fun inferDifficulty(evalSet: YamlEvalSet): String {
        val assertionCount = evalSet.assertions?.size ?: 0
        return when {
            assertionCount >= 8 -> "HARD"
            assertionCount >= 4 -> "MEDIUM"
            else -> "EASY"
        }
    }

    private fun EvalTaskEntity.toDto() = EvalTaskDto(
        id = id,
        name = name,
        description = description,
        input = input,
        successCriteria = successCriteria,
        graderConfig = graderConfig,
        taskType = taskType,
        skillTags = skillTags?.let {
            try {
                @Suppress("UNCHECKED_CAST")
                objectMapper.readValue(it, List::class.java) as? List<String>
            } catch (_: Exception) { null }
        },
        difficulty = difficulty,
        source = source,
        orgId = orgId,
        isActive = isActive
    )
}
