package com.forge.eval.engine.legacy

import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.UUID

/**
 * Imports existing agent-eval YAML eval-sets into the new Forge Eval protocol model.
 *
 * Reads the eval-sets directory structure (organized by profile) and converts
 * each YAML scenario into an EvalSuite + EvalTask pair compatible with the
 * new evaluation engine.
 */
class YamlEvalSetImporter(
    private val evalSetsDir: File = File("agent-eval/eval-sets")
) {

    private val logger = LoggerFactory.getLogger(YamlEvalSetImporter::class.java)
    private val yaml = Yaml()

    data class ImportResult(
        val suites: List<EvalSuite>,
        val tasksBySuite: Map<UUID, List<EvalTask>>,
        val errors: List<String>
    ) {
        val totalTasks: Int get() = tasksBySuite.values.sumOf { it.size }
    }

    /**
     * Import all YAML eval-sets, grouping tasks into suites by profile.
     */
    fun importAll(): ImportResult {
        if (!evalSetsDir.exists()) {
            logger.warn("Eval sets directory not found: {}", evalSetsDir.absolutePath)
            return ImportResult(emptyList(), emptyMap(), listOf("Directory not found: ${evalSetsDir.absolutePath}"))
        }

        val suites = mutableListOf<EvalSuite>()
        val tasksBySuite = mutableMapOf<UUID, MutableList<EvalTask>>()
        val errors = mutableListOf<String>()

        val profileDirs = evalSetsDir.listFiles()?.filter { it.isDirectory } ?: emptyList()

        for (profileDir in profileDirs) {
            val profileName = profileDir.name.removeSuffix("-profile")
            val suite = EvalSuite(
                name = "$profileName eval suite (imported)",
                description = "Auto-imported from legacy eval-sets/$profileName",
                platform = Platform.FORGE,
                agentType = inferAgentType(profileName),
                lifecycle = Lifecycle.CAPABILITY,
                tags = listOf(profileName, "imported")
            )
            suites.add(suite)
            tasksBySuite[suite.id] = mutableListOf()

            val yamlFiles = profileDir.listFiles()
                ?.filter { it.extension == "yaml" || it.extension == "yml" }
                ?.sortedBy { it.name }
                ?: emptyList()

            for (file in yamlFiles) {
                try {
                    val task = parseYamlToTask(file, suite.id, profileName)
                    tasksBySuite[suite.id]!!.add(task)
                } catch (e: Exception) {
                    val msg = "Failed to import ${file.name}: ${e.message}"
                    errors.add(msg)
                    logger.error(msg, e)
                }
            }

            logger.info("Imported profile '{}': {} tasks", profileName, tasksBySuite[suite.id]!!.size)
        }

        logger.info("Import complete: {} suites, {} tasks, {} errors",
            suites.size, tasksBySuite.values.sumOf { it.size }, errors.size)

        return ImportResult(suites, tasksBySuite, errors)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun parseYamlToTask(file: File, suiteId: UUID, profile: String): EvalTask {
        val data = yaml.load<Map<String, Any>>(file.readText())

        val assertions = (data["assertions"] as? List<Map<String, Any>> ?: emptyList())
            .map { parseAssertion(it) }

        val qualityCriteria = (data["quality_criteria"] as? Map<String, Any>)
            ?.mapValues { (it.value as? Number)?.toDouble() ?: 0.0 }
            ?: emptyMap()

        val context = data["context"] as? Map<String, Any> ?: emptyMap()
        val tags = data["tags"] as? List<String> ?: emptyList()

        return EvalTask(
            suiteId = suiteId,
            name = data["name"] as? String ?: file.nameWithoutExtension,
            description = data["description"] as? String ?: "",
            prompt = data["prompt"] as? String ?: "",
            context = context,
            graderConfigs = listOf(
                GraderConfig(
                    type = GraderType.CODE_BASED,
                    assertions = assertions
                )
            ),
            difficulty = inferDifficulty(qualityCriteria),
            tags = tags + profile,
            baselinePassRate = (data["baseline_pass_rate"] as? Number)?.toDouble() ?: 0.8
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAssertion(data: Map<String, Any>): AssertionConfig {
        val type = data["type"] as? String ?: "contains"
        val expected = data["expected"] as? String ?: ""
        val description = data["description"] as? String ?: "Assertion: $type"
        val caseSensitive = (data["case_insensitive"] as? Boolean)?.not() ?: true

        val extras = mutableMapOf<String, Any>()
        data["structure_type"]?.let { extras["structure_type"] = it }

        return AssertionConfig(
            type = type,
            expected = expected,
            description = description,
            caseSensitive = caseSensitive,
            extras = extras
        )
    }

    private fun inferAgentType(profile: String): AgentType {
        return when (profile) {
            "development", "testing", "ops" -> AgentType.CODING
            "planning", "design" -> AgentType.RESEARCH
            "evaluation", "learning-loop" -> AgentType.RESEARCH
            else -> AgentType.CODING
        }
    }

    private fun inferDifficulty(criteria: Map<String, Double>): Difficulty {
        val avg = if (criteria.isNotEmpty()) criteria.values.average() else 0.5
        return when {
            avg >= 0.9 -> Difficulty.HARD
            avg >= 0.75 -> Difficulty.MEDIUM
            else -> Difficulty.EASY
        }
    }
}
