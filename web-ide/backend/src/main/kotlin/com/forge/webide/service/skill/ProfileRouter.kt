package com.forge.webide.service.skill

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Routes user messages to the appropriate Skill Profile.
 *
 * Priority chain (from CLAUDE.md):
 * 1. Explicit user tags (@规划, @设计, @开发, @测试, @运维)
 * 2. Keyword auto-detection (Chinese and English)
 * 3. Workspace context (branch name)
 * 4. Default to development-profile
 */
@Service
class ProfileRouter(
    private val skillLoader: SkillLoader
) {
    private val logger = LoggerFactory.getLogger(ProfileRouter::class.java)

    companion object {
        private const val DEFAULT_PROFILE = "development-profile"

        // Priority 1: Explicit tag → profile name mapping
        private val EXPLICIT_TAGS = mapOf(
            "@规划" to "planning-profile",
            "@设计" to "design-profile",
            "@开发" to "development-profile",
            "@测试" to "testing-profile",
            "@运维" to "ops-profile",
            "@评估" to "evaluation-profile"
        )

        // Priority 2: Keyword → profile name mapping
        // NOTE: evaluation-profile keywords are listed FIRST so they match before development fallback
        private val KEYWORD_ROUTES: List<Pair<List<String>, String>> = listOf(
            // Evaluation/Analysis keywords (must be before development to avoid fallback)
            listOf(
                "progress", "status", "evaluate", "assessment", "report", "analyze quality",
                "进度", "状态", "评估", "汇报", "到哪了", "进展", "看看", "查看进度"
            ) to "evaluation-profile",
            listOf(
                "distill", "extract pattern", "lesson learned", "knowledge base",
                "提炼", "经验总结", "萃取", "沉淀", "复盘", "写入知识库"
            ) to "evaluation-profile",
            listOf(
                "generate doc", "architecture diagram", "manual", "usage guide",
                "手册", "生成文档", "架构图", "报告", "生成报告", "周报"
            ) to "evaluation-profile",
            // Delivery stage keywords
            listOf(
                "requirement", "prd", "user story", "feature request", "scope", "stakeholder",
                "需求", "规划", "产品文档"
            ) to "planning-profile",
            listOf(
                "architecture", "design", "api spec", "schema", "adr", "c4", "sequence diagram",
                "架构", "设计", "接口"
            ) to "design-profile",
            listOf(
                "implement", "code", "build", "fix bug", "refactor", "pr",
                "开发", "编码", "实现", "修复"
            ) to "development-profile",
            listOf(
                "test", "coverage", "qa", "regression", "boundary",
                "测试", "覆盖率", "用例"
            ) to "testing-profile",
            listOf(
                "deploy", "release", "rollback", "monitor", "incident", "kubernetes",
                "部署", "发布", "运维"
            ) to "ops-profile"
        )

        // Priority 3: Branch name patterns
        private val BRANCH_PATTERNS = mapOf(
            Regex("^feature/.*") to "development-profile",
            Regex("^bugfix/.*") to "development-profile",
            Regex("^hotfix/.*") to "ops-profile",
            Regex("^release/.*") to "ops-profile",
            Regex("^test/.*") to "testing-profile",
            Regex("^design/.*") to "design-profile"
        )
    }

    /**
     * Route a user message to the most appropriate Profile.
     *
     * @param message The user's message text
     * @param branchName Optional current git branch name for context-based routing
     */
    fun route(message: String, branchName: String? = null): ProfileRoutingResult {
        // Priority 1: Explicit tags
        val tagResult = routeByExplicitTag(message)
        if (tagResult != null) return tagResult

        // Priority 2: Keyword detection
        val keywordResult = routeByKeyword(message)
        if (keywordResult != null) return keywordResult

        // Priority 3: Branch name context
        if (branchName != null) {
            val branchResult = routeByBranch(branchName)
            if (branchResult != null) return branchResult
        }

        // Priority 4: Default
        return routeToDefault()
    }

    private fun routeByExplicitTag(message: String): ProfileRoutingResult? {
        for ((tag, profileName) in EXPLICIT_TAGS) {
            if (message.contains(tag)) {
                val profile = skillLoader.loadProfile(profileName)
                if (profile != null) {
                    logger.info("Routed to {} via explicit tag '{}'", profileName, tag)
                    return ProfileRoutingResult(
                        profile = profile,
                        confidence = 1.0,
                        reason = "Explicit tag: $tag"
                    )
                }
            }
        }
        return null
    }

    private fun routeByKeyword(message: String): ProfileRoutingResult? {
        val lowerMessage = message.lowercase()
        // Strip punctuation to measure "meaningful" message length
        val strippedMessage = lowerMessage.replace(Regex("[\\s\\p{Punct}]"), "")

        // Score each profile by keyword matches (weighted: short/vague keywords count less)
        var bestProfile: String? = null
        var bestScore = 0.0
        var bestKeyword = ""

        for ((keywords, profileName) in KEYWORD_ROUTES) {
            var score = 0.0
            var matchedKeyword = ""
            for (keyword in keywords) {
                if (lowerMessage.contains(keyword.lowercase())) {
                    // Short keywords (≤2 chars) are vague — give half weight
                    val weight = if (keyword.length <= 2) 0.5 else 1.0
                    score += weight
                    if (matchedKeyword.isEmpty()) matchedKeyword = keyword
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestProfile = profileName
                bestKeyword = matchedKeyword
            }
        }

        if (bestProfile != null && bestScore > 0) {
            val profile = skillLoader.loadProfile(bestProfile)
            if (profile != null) {
                // Base confidence from keyword score
                var confidence = (0.5 + 0.1 * bestScore).coerceAtMost(0.95)
                // Short messages with weak matches → lower confidence to trigger intent confirmation
                if (strippedMessage.length <= 5 && bestScore < 1.0) {
                    confidence = (confidence * 0.7).coerceAtMost(0.45)
                }
                logger.info(
                    "Routed to {} via keyword '{}' (score={}, confidence={}, msgLen={})",
                    bestProfile, bestKeyword, bestScore, confidence, strippedMessage.length
                )
                return ProfileRoutingResult(
                    profile = profile,
                    confidence = confidence,
                    reason = "Keyword detected: $bestKeyword (score=$bestScore)"
                )
            }
        }

        return null
    }

    private fun routeByBranch(branchName: String): ProfileRoutingResult? {
        for ((pattern, profileName) in BRANCH_PATTERNS) {
            if (pattern.matches(branchName)) {
                val profile = skillLoader.loadProfile(profileName)
                if (profile != null) {
                    logger.info("Routed to {} via branch name '{}'", profileName, branchName)
                    return ProfileRoutingResult(
                        profile = profile,
                        confidence = 0.6,
                        reason = "Branch context: $branchName"
                    )
                }
            }
        }
        return null
    }

    private fun routeToDefault(): ProfileRoutingResult {
        val profile = skillLoader.loadProfile(DEFAULT_PROFILE)
        if (profile != null) {
            logger.debug("Routed to default profile: {}", DEFAULT_PROFILE)
            return ProfileRoutingResult(
                profile = profile,
                confidence = 0.3,
                reason = "Default fallback"
            )
        }

        // Ultimate fallback: create a minimal development profile
        logger.warn("Default profile '{}' not found, using built-in fallback", DEFAULT_PROFILE)
        return ProfileRoutingResult(
            profile = ProfileDefinition(
                name = DEFAULT_PROFILE,
                description = "Default development profile (built-in fallback)",
                skills = emptyList(),
                baselines = emptyList(),
                hitlCheckpoint = "",
                oodaGuidance = "",
                sourcePath = "built-in"
            ),
            confidence = 0.1,
            reason = "Built-in fallback (no profiles loaded)"
        )
    }
}
