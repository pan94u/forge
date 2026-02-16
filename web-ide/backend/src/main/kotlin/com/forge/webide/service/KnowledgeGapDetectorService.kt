package com.forge.webide.service

import com.forge.webide.model.ContextReference
import com.forge.webide.model.KnowledgeGap
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects and logs knowledge gaps based on AI conversation patterns.
 *
 * When the AI assistant cannot find relevant information in the knowledge base,
 * or when users repeatedly ask about undocumented topics, this service logs
 * those gaps so the team can prioritize documentation efforts.
 */
@Service
class KnowledgeGapDetectorService {

    private val logger = LoggerFactory.getLogger(KnowledgeGapDetectorService::class.java)

    private val gaps = ConcurrentHashMap<String, KnowledgeGap>()

    // Phrases that indicate the AI couldn't find relevant knowledge
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

    // Topics that are commonly asked but may lack documentation
    private val topicTracker = ConcurrentHashMap<String, Int>()

    /**
     * Analyze a conversation exchange for potential knowledge gaps.
     */
    fun analyzeForGaps(
        userQuery: String,
        aiResponse: String,
        contexts: List<ContextReference>
    ) {
        // Check if the AI response indicates a knowledge gap
        val hasGapIndicator = gapIndicators.any { indicator ->
            aiResponse.lowercase().contains(indicator.lowercase())
        }

        if (hasGapIndicator) {
            val gap = KnowledgeGap(
                query = userQuery,
                context = buildContextSummary(contexts)
            )
            gaps[gap.id] = gap
            logger.info("Knowledge gap detected: query='${userQuery.take(100)}', id=${gap.id}")
        }

        // Track topic frequency
        val topics = extractTopics(userQuery)
        topics.forEach { topic ->
            val count = topicTracker.merge(topic, 1) { a, b -> a + b } ?: 1
            if (count >= 3 && !hasExistingGap(topic)) {
                val gap = KnowledgeGap(
                    query = "Frequently asked topic: $topic (asked $count times)",
                    context = "Auto-detected from repeated questions"
                )
                gaps[gap.id] = gap
                logger.info("Frequent topic gap detected: topic='$topic', count=$count")
            }
        }

        // Check for empty context responses (user tried to reference knowledge that doesn't exist)
        if (contexts.any { it.content.isNullOrBlank() }) {
            val missingContexts = contexts.filter { it.content.isNullOrBlank() }
            missingContexts.forEach { ctx ->
                val gap = KnowledgeGap(
                    query = "Missing context: ${ctx.type}/${ctx.id}",
                    context = "User referenced ${ctx.type} '${ctx.id}' but content was empty"
                )
                gaps[gap.id] = gap
                logger.info("Missing context gap: type=${ctx.type}, id=${ctx.id}")
            }
        }
    }

    /**
     * Get all unresolved knowledge gaps.
     */
    fun getUnresolvedGaps(): List<KnowledgeGap> {
        return gaps.values
            .filter { !it.resolved }
            .sortedByDescending { it.detectedAt }
    }

    /**
     * Get all knowledge gaps.
     */
    fun getAllGaps(): List<KnowledgeGap> {
        return gaps.values.sortedByDescending { it.detectedAt }
    }

    /**
     * Mark a knowledge gap as resolved.
     */
    fun resolveGap(gapId: String): KnowledgeGap? {
        val gap = gaps[gapId] ?: return null
        val resolved = gap.copy(
            resolved = true,
            resolvedAt = Instant.now()
        )
        gaps[gapId] = resolved
        logger.info("Knowledge gap resolved: id=$gapId")
        return resolved
    }

    /**
     * Get statistics about knowledge gaps.
     */
    fun getGapStatistics(): Map<String, Any> {
        val allGaps = gaps.values.toList()
        return mapOf(
            "total" to allGaps.size,
            "unresolved" to allGaps.count { !it.resolved },
            "resolved" to allGaps.count { it.resolved },
            "frequentTopics" to topicTracker.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { mapOf("topic" to it.key, "count" to it.value) }
        )
    }

    private fun buildContextSummary(contexts: List<ContextReference>): String {
        if (contexts.isEmpty()) return "No context provided"
        return contexts.joinToString(", ") { "${it.type}:${it.id}" }
    }

    private fun extractTopics(query: String): List<String> {
        // Simple topic extraction: look for key phrases
        val topics = mutableListOf<String>()

        // Extract quoted terms
        val quoteRegex = Regex("\"([^\"]+)\"")
        quoteRegex.findAll(query).forEach { match ->
            topics.add(match.groupValues[1].lowercase())
        }

        // Extract service/component names (capitalized words or hyphenated words)
        val nameRegex = Regex("\\b([A-Z][a-z]+(?:[A-Z][a-z]+)*|[a-z]+-[a-z]+(?:-[a-z]+)*)\\b")
        nameRegex.findAll(query).forEach { match ->
            topics.add(match.groupValues[1].lowercase())
        }

        return topics.distinct()
    }

    private fun hasExistingGap(topic: String): Boolean {
        return gaps.values.any { gap ->
            gap.query.lowercase().contains(topic) && !gap.resolved
        }
    }
}
