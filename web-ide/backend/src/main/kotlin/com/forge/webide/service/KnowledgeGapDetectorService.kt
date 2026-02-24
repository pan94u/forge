package com.forge.webide.service

import com.forge.webide.entity.KnowledgeGapEntity
import com.forge.webide.model.ContextReference
import com.forge.webide.model.KnowledgeGap
import com.forge.webide.repository.KnowledgeGapRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Detects and logs knowledge gaps based on AI conversation patterns.
 * Phase 8.3: Upgraded from in-memory ConcurrentHashMap to DB persistence.
 *
 * When the AI assistant cannot find relevant information in the knowledge base,
 * or when users repeatedly ask about undocumented topics, this service logs
 * those gaps so the team can prioritize documentation efforts.
 *
 * Auto-resolve: topics queried 5+ times without resolution automatically
 * generate a knowledge document stub.
 */
@Service
class KnowledgeGapDetectorService(
    private val knowledgeGapRepository: KnowledgeGapRepository
) {

    private val logger = LoggerFactory.getLogger(KnowledgeGapDetectorService::class.java)

    companion object {
        const val AUTO_STUB_THRESHOLD = 5
    }

    private val gapIndicators = listOf(
        "I don't have information about",
        "I couldn't find documentation for",
        "There doesn't appear to be",
        "I'm not sure about",
        "no documentation available",
        "not documented",
        "couldn't locate",
        "I don't have access to that information"
    )

    /**
     * Analyze a conversation exchange for potential knowledge gaps.
     */
    fun analyzeForGaps(
        userQuery: String,
        aiResponse: String,
        contexts: List<ContextReference>,
        workspaceId: String? = null
    ) {
        val hasGapIndicator = gapIndicators.any { indicator ->
            aiResponse.lowercase().contains(indicator.lowercase())
        }

        if (hasGapIndicator) {
            persistGap(
                query = userQuery,
                context = buildContextSummary(contexts),
                topic = extractPrimaryTopic(userQuery),
                workspaceId = workspaceId
            )
        }

        // Track topic frequency
        val topics = extractTopics(userQuery)
        topics.forEach { topic ->
            incrementTopicCount(topic, workspaceId)
        }

        // Check for empty context responses
        if (contexts.any { it.content.isNullOrBlank() }) {
            val missingContexts = contexts.filter { it.content.isNullOrBlank() }
            missingContexts.forEach { ctx ->
                persistGap(
                    query = "Missing context: ${ctx.type}/${ctx.id}",
                    context = "User referenced ${ctx.type} '${ctx.id}' but content was empty",
                    topic = ctx.id,
                    workspaceId = workspaceId
                )
            }
        }
    }

    /**
     * Get all unresolved knowledge gaps.
     */
    fun getUnresolvedGaps(): List<KnowledgeGap> {
        return knowledgeGapRepository.findByResolved(false)
            .map { it.toModel() }
            .sortedByDescending { it.detectedAt }
    }

    /**
     * Get all knowledge gaps.
     */
    fun getAllGaps(): List<KnowledgeGap> {
        return knowledgeGapRepository.findAll()
            .map { it.toModel() }
            .sortedByDescending { it.detectedAt }
    }

    /**
     * Mark a knowledge gap as resolved.
     */
    fun resolveGap(gapId: String): KnowledgeGap? {
        val entity = knowledgeGapRepository.findById(gapId).orElse(null) ?: return null
        entity.resolved = true
        entity.resolvedAt = Instant.now()
        entity.updatedAt = Instant.now()
        knowledgeGapRepository.save(entity)
        logger.info("Knowledge gap resolved: id=$gapId")
        return entity.toModel()
    }

    /**
     * Get gaps that need auto-stub creation (hit count >= threshold, not yet stubbed).
     */
    fun getGapsNeedingStubs(): List<KnowledgeGapEntity> {
        return knowledgeGapRepository.findFrequentUnresolved(AUTO_STUB_THRESHOLD)
            .filter { !it.autoStubCreated }
    }

    /**
     * Mark a gap as having had its stub created.
     */
    fun markStubCreated(gapId: String) {
        val entity = knowledgeGapRepository.findById(gapId).orElse(null) ?: return
        entity.autoStubCreated = true
        entity.updatedAt = Instant.now()
        knowledgeGapRepository.save(entity)
    }

    /**
     * Get statistics about knowledge gaps.
     */
    fun getGapStatistics(): Map<String, Any> {
        val all = knowledgeGapRepository.findAll()
        val frequentTopics = all
            .filter { !it.topic.isNullOrBlank() }
            .groupBy { it.topic!! }
            .mapValues { it.value.sumOf { g -> g.hitCount } }
            .entries
            .sortedByDescending { it.value }
            .take(10)
            .map { mapOf("topic" to it.key, "count" to it.value) }

        return mapOf(
            "total" to all.size,
            "unresolved" to all.count { !it.resolved },
            "resolved" to all.count { it.resolved },
            "autoStubbed" to all.count { it.autoStubCreated },
            "frequentTopics" to frequentTopics
        )
    }

    // ---- Internal ----

    private fun persistGap(query: String, context: String, topic: String?, workspaceId: String?) {
        try {
            // Check for existing gap with same topic
            if (!topic.isNullOrBlank()) {
                val existing = knowledgeGapRepository.findUnresolvedByTopic(topic)
                if (existing.isNotEmpty()) {
                    val gap = existing.first()
                    gap.hitCount++
                    gap.updatedAt = Instant.now()
                    knowledgeGapRepository.save(gap)
                    logger.debug("Incremented hit count for gap: topic={}, count={}", topic, gap.hitCount)
                    return
                }
            }

            val entity = KnowledgeGapEntity(
                id = UUID.randomUUID().toString(),
                query = query,
                context = context,
                topic = topic,
                workspaceId = workspaceId
            )
            knowledgeGapRepository.save(entity)
            logger.info("Knowledge gap detected: query='${query.take(100)}', topic=$topic")
        } catch (e: Exception) {
            logger.warn("Failed to persist knowledge gap: {}", e.message)
        }
    }

    private fun incrementTopicCount(topic: String, workspaceId: String?) {
        try {
            val existing = knowledgeGapRepository.findUnresolvedByTopic(topic)
            if (existing.isNotEmpty()) {
                val gap = existing.first()
                gap.hitCount++
                gap.updatedAt = Instant.now()
                knowledgeGapRepository.save(gap)
            } else {
                val entity = KnowledgeGapEntity(
                    id = UUID.randomUUID().toString(),
                    query = "Frequently asked topic: $topic",
                    context = "Auto-detected from repeated questions",
                    topic = topic,
                    workspaceId = workspaceId
                )
                knowledgeGapRepository.save(entity)
            }
        } catch (e: Exception) {
            logger.debug("Failed to increment topic count for {}: {}", topic, e.message)
        }
    }

    private fun buildContextSummary(contexts: List<ContextReference>): String {
        if (contexts.isEmpty()) return "No context provided"
        return contexts.joinToString(", ") { "${it.type}:${it.id}" }
    }

    private fun extractPrimaryTopic(query: String): String? {
        // Extract the first quoted term or service name
        val quoteRegex = Regex("\"([^\"]+)\"")
        val match = quoteRegex.find(query)
        if (match != null) return match.groupValues[1].lowercase()

        val nameRegex = Regex("\\b([A-Z][a-z]+(?:[A-Z][a-z]+)*|[a-z]+-[a-z]+(?:-[a-z]+)*)\\b")
        val nameMatch = nameRegex.find(query)
        if (nameMatch != null) return nameMatch.groupValues[1].lowercase()

        return null
    }

    private fun extractTopics(query: String): List<String> {
        val topics = mutableListOf<String>()
        val quoteRegex = Regex("\"([^\"]+)\"")
        quoteRegex.findAll(query).forEach { match ->
            topics.add(match.groupValues[1].lowercase())
        }
        val nameRegex = Regex("\\b([A-Z][a-z]+(?:[A-Z][a-z]+)*|[a-z]+-[a-z]+(?:-[a-z]+)*)\\b")
        nameRegex.findAll(query).forEach { match ->
            topics.add(match.groupValues[1].lowercase())
        }
        return topics.distinct()
    }

    private fun KnowledgeGapEntity.toModel() = KnowledgeGap(
        id = this.id,
        query = this.query,
        context = this.context ?: "",
        detectedAt = this.createdAt,
        resolved = this.resolved,
        resolvedAt = this.resolvedAt
    )
}
