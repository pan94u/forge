package com.forge.webide.service

import com.forge.webide.entity.KnowledgeTagEntity
import com.forge.webide.model.*
import com.forge.webide.repository.KnowledgeTagRepository
import com.forge.webide.repository.WorkspaceRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.File
import java.time.Instant

@Service
class KnowledgeTagService(
    private val knowledgeTagRepository: KnowledgeTagRepository,
    @Value("\${forge.knowledge.baseline-path:}") private val baselinePath: String,
    private val workspaceRepository: WorkspaceRepository
) {

    private val logger = LoggerFactory.getLogger(KnowledgeTagService::class.java)

    data class ChapterDef(
        val sortOrder: Int,
        val id: String,
        val name: String,
        val chapterHeading: String
    )

    private val chapterDefs = listOf(
        ChapterDef(0, "ui-ux", "UI/UX 设计基线", "一、UI/UX 设计基线"),
        ChapterDef(1, "api-contract", "API 契约基线", "二、API 契约基线"),
        ChapterDef(2, "data-model", "数据模型基线", "三、数据模型基线"),
        ChapterDef(3, "arch-decision", "架构决策基线", "四、架构决策基线"),
        ChapterDef(4, "verification", "验证状态", "五、验证状态"),
        ChapterDef(5, "frontend-spec", "前端设计规范", "六、前端设计规范（开发参考）"),
        ChapterDef(6, "change-rules", "变更规则", "七、变更规则"),
        ChapterDef(7, "flow-diagrams", "业务流程图", "八、业务流程图")
    )

    init {
        importFromBaseline()
    }

    // =========================================================================
    // CRUD
    // =========================================================================

    fun listTags(workspaceId: String? = null): List<KnowledgeTag> {
        if (workspaceId.isNullOrBlank()) {
            return knowledgeTagRepository.findByWorkspaceIdIsNullOrderBySortOrderAsc().map { it.toModel() }
        }
        // Auto-initialize workspace tags on first access, or backfill when new tags are added
        if (knowledgeTagRepository.countByWorkspaceId(workspaceId) < chapterDefs.size) {
            // BUG-051: Guard against orphan tag rebuild after workspace deletion
            if (!workspaceRepository.existsById(workspaceId)) return emptyList()
            initializeWorkspaceTags(workspaceId)
        }
        return knowledgeTagRepository.findByWorkspaceIdOrderBySortOrderAsc(workspaceId).map { it.toModel() }
    }

    fun getTag(id: String): KnowledgeTag? {
        return knowledgeTagRepository.findById(id).orElse(null)?.toModel()
    }

    fun createTag(request: CreateKnowledgeTagRequest): KnowledgeTag {
        val entity = KnowledgeTagEntity(
            id = request.id,
            name = request.name,
            description = request.description,
            chapterHeading = request.chapterHeading,
            content = request.content,
            sortOrder = request.sortOrder,
            sourceFile = request.sourceFile
        )
        knowledgeTagRepository.save(entity)
        logger.info("Created knowledge tag: {} ({})", entity.name, entity.id)
        return entity.toModel()
    }

    fun updateTag(id: String, request: UpdateKnowledgeTagRequest): KnowledgeTag? {
        val entity = knowledgeTagRepository.findById(id).orElse(null) ?: return null
        request.name?.let { entity.name = it }
        request.description?.let { entity.description = it }
        request.chapterHeading?.let { entity.chapterHeading = it }
        request.content?.let { entity.content = it }
        request.status?.let { entity.status = it }
        request.sourceFile?.let { entity.sourceFile = it }
        entity.updatedAt = Instant.now()
        knowledgeTagRepository.save(entity)
        logger.info("Updated knowledge tag: {} ({})", entity.name, id)
        return entity.toModel()
    }

    fun deleteTag(id: String): Boolean {
        if (!knowledgeTagRepository.existsById(id)) return false
        knowledgeTagRepository.deleteById(id)
        logger.info("Deleted knowledge tag: {}", id)
        return true
    }

    // =========================================================================
    // Workspace tag lifecycle
    // =========================================================================

    fun initializeWorkspaceTags(workspaceId: String) {
        try {
            chapterDefs.forEach { def ->
                val compoundId = "${workspaceId}_${def.id}"
                if (!knowledgeTagRepository.existsById(compoundId)) {
                    val entity = KnowledgeTagEntity(
                        id = compoundId,
                        name = def.name,
                        description = "",
                        chapterHeading = def.chapterHeading,
                        content = "",
                        sortOrder = def.sortOrder,
                        status = "empty",
                        workspaceId = workspaceId,
                        tagKey = def.id
                    )
                    knowledgeTagRepository.save(entity)
                }
            }
            logger.info("Initialized {} workspace tags for workspace {}", chapterDefs.size, workspaceId)
        } catch (e: Exception) {
            logger.warn("Failed to initialize workspace tags (may already exist): {}", e.message)
        }
    }

    @Transactional
    fun deleteWorkspaceTags(workspaceId: String) {
        knowledgeTagRepository.deleteByWorkspaceId(workspaceId)
        logger.info("Deleted workspace tags for workspace {}", workspaceId)
    }

    // =========================================================================
    // Reorder
    // =========================================================================

    fun reorderTags(tagIds: List<String>): List<KnowledgeTag> {
        tagIds.forEachIndexed { index, tagId ->
            knowledgeTagRepository.findById(tagId).ifPresent { entity ->
                entity.sortOrder = index
                entity.updatedAt = Instant.now()
                knowledgeTagRepository.save(entity)
            }
        }
        logger.info("Reordered {} knowledge tags", tagIds.size)
        return listTags()
    }

    // =========================================================================
    // Search
    // =========================================================================

    fun searchTags(query: String, workspaceId: String? = null): List<KnowledgeTag> {
        if (query.isBlank()) return listTags(workspaceId)
        return knowledgeTagRepository
            .findByNameContainingIgnoreCaseOrContentContainingIgnoreCase(query, query)
            .filter { if (workspaceId.isNullOrBlank()) it.workspaceId == null else it.workspaceId == workspaceId }
            .sortedBy { it.sortOrder }
            .map { it.toModel() }
    }

    // =========================================================================
    // Import from baseline
    // =========================================================================

    fun importFromBaseline(): Boolean {
        if (knowledgeTagRepository.findByWorkspaceIdIsNullOrderBySortOrderAsc().isNotEmpty()) {
            logger.info("Global knowledge tags already exist, skipping baseline import")
            return false
        }

        if (baselinePath.isBlank()) {
            logger.info("No baseline path configured, skipping import")
            return false
        }

        val file = File(baselinePath)
        if (!file.exists()) {
            logger.info("Baseline file not found at {}, skipping import", baselinePath)
            return false
        }

        return try {
            val content = file.readText()
            val chapters = splitChapters(content)

            chapters.forEach { (def, chapterContent) ->
                val entity = KnowledgeTagEntity(
                    id = def.id,
                    name = def.name,
                    description = extractFirstParagraph(chapterContent),
                    chapterHeading = def.chapterHeading,
                    content = chapterContent.trim(),
                    sortOrder = def.sortOrder,
                    sourceFile = baselinePath,
                    tagKey = def.id
                )
                knowledgeTagRepository.save(entity)
            }
            logger.info("Imported {} knowledge tags from baseline", chapters.size)
            true
        } catch (e: Exception) {
            logger.error("Failed to import baseline: {}", e.message)
            false
        }
    }

    // =========================================================================
    // Internal helpers
    // =========================================================================

    private fun splitChapters(content: String): List<Pair<ChapterDef, String>> {
        val chapterPattern = Regex("^## [一二三四五六七八]、", RegexOption.MULTILINE)
        val matches = chapterPattern.findAll(content).toList()

        if (matches.isEmpty()) return emptyList()

        val result = mutableListOf<Pair<ChapterDef, String>>()

        for (i in matches.indices) {
            val start = matches[i].range.first
            val end = if (i < matches.size - 1) matches[i + 1].range.first else content.length
            val chapterContent = content.substring(start, end)

            // Match chapter def by the Chinese number
            val chapterDef = chapterDefs.getOrNull(i) ?: continue
            result.add(chapterDef to chapterContent)
        }

        return result
    }

    private fun extractFirstParagraph(content: String): String {
        val lines = content.lines()
        val textLines = lines.drop(1) // skip heading line
            .dropWhile { it.isBlank() }
            .takeWhile { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("```") }
        return textLines.joinToString(" ").take(500)
    }

    private fun KnowledgeTagEntity.toModel(): KnowledgeTag {
        return KnowledgeTag(
            id = id,
            name = name,
            description = description,
            chapterHeading = chapterHeading,
            content = content,
            sortOrder = sortOrder,
            status = status,
            sourceFile = sourceFile,
            workspaceId = workspaceId,
            tagKey = tagKey,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
