package com.forge.webide.service.learning

import com.forge.webide.entity.KnowledgeDocumentEntity
import com.forge.webide.model.DocumentType
import com.forge.webide.model.KnowledgeScope
import com.forge.webide.repository.ExecutionRecordRepository
import com.forge.webide.repository.KnowledgeDocumentRepository
import com.forge.webide.service.KnowledgeGapDetectorService
import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Execution Asset Extractor (Phase 8.3 — Pipeline 1).
 *
 * Extracts reusable knowledge from execution records:
 * - Code patterns and tool call sequences
 * - Error fix patterns (what broke → how it was fixed)
 * - Frequently used tool combinations
 *
 * Triggered every 10 executions per workspace, or manually via pipeline.
 */
@Service
class AssetExtractorService(
    private val executionRecordRepository: ExecutionRecordRepository,
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository,
    private val knowledgeGapDetectorService: KnowledgeGapDetectorService
) {
    private val logger = LoggerFactory.getLogger(AssetExtractorService::class.java)
    private val gson = Gson()

    companion object {
        const val EXTRACTION_INTERVAL = 10
        const val MIN_RECORDS_FOR_EXTRACTION = 5
    }

    /**
     * Run asset extraction for a workspace.
     * Analyzes recent execution records and generates knowledge documents.
     */
    fun extractAssets(workspaceId: String? = null, days: Int = 7): AssetExtractionResult {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val records = executionRecordRepository.findByCreatedAtAfter(since)

        if (records.size < MIN_RECORDS_FOR_EXTRACTION) {
            return AssetExtractionResult(
                documentsCreated = 0,
                patternsFound = 0,
                message = "Insufficient data (${records.size} < $MIN_RECORDS_FOR_EXTRACTION)"
            )
        }

        var documentsCreated = 0
        var patternsFound = 0

        // 1. Extract tool usage patterns
        val toolPatterns = extractToolPatterns(records)
        if (toolPatterns.isNotBlank()) {
            saveKnowledgeDocument(
                title = "Tool Usage Patterns (Auto-extracted)",
                type = "pattern",
                content = toolPatterns,
                scope = if (workspaceId != null) "WORKSPACE" else "GLOBAL",
                scopeId = workspaceId
            )
            documentsCreated++
            patternsFound += toolPatterns.lines().count { it.startsWith("-") }
        }

        // 2. Extract profile usage patterns
        val profilePatterns = extractProfilePatterns(records)
        if (profilePatterns.isNotBlank()) {
            saveKnowledgeDocument(
                title = "Profile Usage Patterns (Auto-extracted)",
                type = "pattern",
                content = profilePatterns,
                scope = if (workspaceId != null) "WORKSPACE" else "GLOBAL",
                scopeId = workspaceId
            )
            documentsCreated++
        }

        // 3. Auto-resolve knowledge gaps by creating stubs
        val stubs = createGapStubs()
        documentsCreated += stubs

        logger.info("Asset extraction complete: {} documents created, {} patterns found, {} stubs",
            documentsCreated, patternsFound, stubs)

        return AssetExtractionResult(
            documentsCreated = documentsCreated,
            patternsFound = patternsFound,
            gapStubsCreated = stubs,
            message = "Extraction complete"
        )
    }

    /**
     * Extract frequently used tool combinations from execution records.
     */
    @Suppress("UNCHECKED_CAST")
    private fun extractToolPatterns(records: List<com.forge.webide.entity.ExecutionRecordEntity>): String {
        val toolSequences = mutableMapOf<String, Int>()

        for (rec in records) {
            try {
                val tools = gson.fromJson(rec.toolCalls, List::class.java) as? List<Map<String, Any>> ?: continue
                val names = tools.mapNotNull { it["name"] as? String }
                if (names.size >= 2) {
                    // Track pairs
                    for (i in 0 until names.size - 1) {
                        val pair = "${names[i]} → ${names[i + 1]}"
                        toolSequences[pair] = (toolSequences[pair] ?: 0) + 1
                    }
                }
            } catch (_: Exception) { }
        }

        val frequentPairs = toolSequences.entries
            .filter { it.value >= 3 }
            .sortedByDescending { it.value }
            .take(10)

        if (frequentPairs.isEmpty()) return ""

        return buildString {
            appendLine("# Frequently Used Tool Sequences")
            appendLine()
            appendLine("Auto-extracted from ${records.size} recent executions.")
            appendLine()
            for ((pair, count) in frequentPairs) {
                appendLine("- **$pair** ($count occurrences)")
            }
        }
    }

    /**
     * Extract profile usage statistics.
     */
    private fun extractProfilePatterns(records: List<com.forge.webide.entity.ExecutionRecordEntity>): String {
        val byProfile = records.groupBy { it.profile }
        if (byProfile.size < 2) return ""

        return buildString {
            appendLine("# Profile Usage Summary")
            appendLine()
            appendLine("Auto-extracted from ${records.size} recent executions.")
            appendLine()
            appendLine("| Profile | Executions | Avg Duration | Avg Turns |")
            appendLine("|---------|-----------|-------------|-----------|")
            for ((profile, recs) in byProfile.entries.sortedByDescending { it.value.size }) {
                val avgDuration = recs.map { it.totalDurationMs }.average()
                val avgTurns = recs.map { it.totalTurns }.average()
                appendLine("| ${profile.replace("-profile", "")} | ${recs.size} | ${"%.0f".format(avgDuration)}ms | ${"%.1f".format(avgTurns)} |")
            }
        }
    }

    /**
     * Auto-create knowledge document stubs for frequently-hit gaps.
     */
    private fun createGapStubs(): Int {
        var count = 0
        try {
            val gaps = knowledgeGapDetectorService.getGapsNeedingStubs()
            for (gap in gaps) {
                val stubContent = buildString {
                    appendLine("# ${gap.topic ?: gap.query.take(80)}")
                    appendLine()
                    appendLine("> This document was auto-created because this topic was queried ${gap.hitCount} times without matching knowledge.")
                    appendLine()
                    appendLine("## Context")
                    appendLine()
                    appendLine(gap.query)
                    appendLine()
                    appendLine("## TODO")
                    appendLine()
                    appendLine("- [ ] Add relevant documentation for this topic")
                }

                saveKnowledgeDocument(
                    title = "Knowledge Stub: ${gap.topic ?: gap.query.take(80)}",
                    type = "stub",
                    content = stubContent,
                    scope = if (gap.workspaceId != null) "WORKSPACE" else "GLOBAL",
                    scopeId = gap.workspaceId
                )
                knowledgeGapDetectorService.markStubCreated(gap.id)
                count++
            }
        } catch (e: Exception) {
            logger.warn("Failed to create gap stubs: {}", e.message)
        }
        return count
    }

    private fun saveKnowledgeDocument(
        title: String,
        type: String,
        content: String,
        scope: String,
        scopeId: String?
    ) {
        try {
            val docType = try { DocumentType.valueOf(type.uppercase()) } catch (_: Exception) { DocumentType.WIKI }
            val docScope = try { KnowledgeScope.valueOf(scope.uppercase()) } catch (_: Exception) { KnowledgeScope.GLOBAL }

            // Check if document with same title already exists
            val existing = knowledgeDocumentRepository.findByTitleAndScope(title, docScope)
            if (existing != null) {
                existing.content = content
                existing.updatedAt = Instant.now()
                knowledgeDocumentRepository.save(existing)
                return
            }

            knowledgeDocumentRepository.save(KnowledgeDocumentEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                type = docType,
                content = content,
                snippet = content.take(200),
                author = "AssetExtractor",
                scope = docScope,
                scopeId = scopeId
            ))
        } catch (e: Exception) {
            logger.warn("Failed to save knowledge document '{}': {}", title, e.message)
        }
    }

    data class AssetExtractionResult(
        val documentsCreated: Int,
        val patternsFound: Int = 0,
        val gapStubsCreated: Int = 0,
        val message: String
    )
}
