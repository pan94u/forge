package com.forge.webide.service.skill

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Routes user messages directly to Skill lists, bypassing the Profile abstraction.
 *
 * Three-layer priority routing:
 * 1. @tag aliases — explicit skill-group selection (confidence=1.0)
 * 2. Keyword → skill mapping — semantic keyword detection (confidence=0.7-0.9)
 * 3. Default fallback — delivery-methodology + code-generation (confidence=0.3)
 *
 * @tag aliases are backward-compatible with the old ProfileRouter tag syntax.
 */
@Service
class IntentSkillRouter {

    private val logger = LoggerFactory.getLogger(IntentSkillRouter::class.java)

    companion object {
        // Layer 1: @tag → skill list + profile hint (for memory loading)
        private val TAG_ROUTES: Map<String, TagRoute> = mapOf(
            "@开发" to TagRoute(
                skills = listOf(
                    "code-generation", "bug-fix-workflow", "kotlin-conventions",
                    "spring-boot-patterns", "database-patterns", "testing-standards",
                    "error-handling", "api-design"
                ),
                baselines = listOf("code-style-baseline", "security-baseline"),
                mode = "default",
                profileHint = "development-profile"
            ),
            "@设计" to TagRoute(
                skills = listOf("architecture-design", "detailed-design", "api-design", "document-generation"),
                baselines = listOf("architecture-baseline"),
                mode = "default",
                profileHint = "design-profile"
            ),
            "@测试" to TagRoute(
                skills = listOf("test-case-writing", "testing-standards", "kotlin-conventions"),
                baselines = listOf("test-coverage-baseline"),
                mode = "default",
                profileHint = "testing-profile"
            ),
            "@规划" to TagRoute(
                skills = listOf("planning-mode", "requirement-engineering", "document-generation", "delivery-methodology"),
                baselines = emptyList(),
                mode = "default",
                profileHint = "planning-profile"
            ),
            "@运维" to TagRoute(
                skills = listOf("deployment-ops", "kubernetes-patterns", "ci-cd-patterns"),
                baselines = emptyList(),
                mode = "default",
                profileHint = "ops-profile"
            ),
            "@评估" to TagRoute(
                skills = listOf("progress-evaluation", "codebase-profiler"),
                baselines = emptyList(),
                mode = "read-only",
                profileHint = "evaluation-profile"
            ),
            "@git" to TagRoute(
                skills = listOf("git-workflow"),
                baselines = emptyList(),
                mode = "default",
                profileHint = "development-profile"
            )
        )

        // Layer 2: keyword patterns → skill list
        private val KEYWORD_ROUTES: List<KeywordRoute> = listOf(
            KeywordRoute(
                keywords = listOf("git", "提交", "commit", "push", "pull", "branch", "gitignore", "版本控制", "merge", "rebase"),
                skills = listOf("git-workflow"),
                confidence = 0.9,
                profileHint = "development-profile"
            ),
            KeywordRoute(
                keywords = listOf("测试", "test", "spec", "单测", "tdd", "junit", "coverage", "覆盖率", "用例"),
                skills = listOf("test-case-writing", "testing-standards"),
                confidence = 0.85,
                profileHint = "testing-profile"
            ),
            KeywordRoute(
                keywords = listOf("架构", "设计", "uml", "er图", "c4", "sequence diagram", "architecture", "design", "schema", "adr"),
                skills = listOf("architecture-design", "detailed-design"),
                confidence = 0.8,
                profileHint = "design-profile"
            ),
            KeywordRoute(
                keywords = listOf("api", "接口", "rest", "endpoint", "controller", "swagger", "openapi"),
                skills = listOf("api-design", "spring-boot-patterns"),
                confidence = 0.8,
                profileHint = "development-profile"
            ),
            KeywordRoute(
                keywords = listOf("数据库", "sql", "schema", "表设计", "entity", "jpa", "hibernate", "migration"),
                skills = listOf("database-patterns"),
                confidence = 0.8,
                profileHint = "development-profile"
            ),
            KeywordRoute(
                keywords = listOf("部署", "docker", "k8s", "kubernetes", "deploy", "release", "rollout", "运维", "helm"),
                skills = listOf("deployment-ops", "kubernetes-patterns"),
                confidence = 0.85,
                profileHint = "ops-profile"
            ),
            KeywordRoute(
                keywords = listOf("文档", "prd", "需求", "用户故事", "feature request", "requirement", "规划", "document"),
                skills = listOf("requirement-engineering", "document-generation"),
                confidence = 0.8,
                profileHint = "planning-profile"
            ),
            KeywordRoute(
                keywords = listOf("bug", "修复", "fix", "报错", "异常", "exception", "error", "crash", "问题"),
                skills = listOf("bug-fix-workflow"),
                confidence = 0.85,
                profileHint = "development-profile"
            ),
            KeywordRoute(
                keywords = listOf(
                    "帮我实现", "帮我做", "帮我完成", "帮我重构", "帮我开发", "帮我加", "帮我增加", "帮我升级",
                    "重构整个", "实现整个", "完整实现", "全面改造", "整体迁移", "整个模块", "所有文件",
                    "planning mode", "任务拆解", "分步骤执行"
                ),
                skills = listOf("planning-mode", "delivery-methodology", "code-generation"),
                confidence = 0.85,
                profileHint = "development-profile"
            ),
            KeywordRoute(
                keywords = listOf("代码", "实现", "写", "生成", "implement", "code", "build", "开发", "编码"),
                skills = listOf("code-generation"),
                confidence = 0.7,
                profileHint = "development-profile"
            ),
            KeywordRoute(
                keywords = listOf("评估", "分析", "报告", "进度", "状态", "evaluate", "assess", "progress", "status report"),
                skills = listOf("progress-evaluation", "codebase-profiler"),
                confidence = 0.8,
                profileHint = "evaluation-profile",
                mode = "read-only"
            )
        )

        // Layer 3: default skills (confidence=0.3)
        private val DEFAULT_SKILLS = listOf("delivery-methodology", "code-generation", "error-handling")
        private val DEFAULT_PROFILE_HINT = "development-profile"

        // Tech stack detection keywords → additional skills to append
        private val KOTLIN_INDICATORS = listOf(".kt", "kotlin", "coroutine", "suspend fun")
        private val SPRING_INDICATORS = listOf("build.gradle", "spring", "@restcontroller", "@service", "@entity")
    }

    /**
     * Analyze user message and workspace context to select the most appropriate skills.
     *
     * @param message User message text
     * @param workspaceId Optional workspace ID (for future context-based enhancement)
     */
    fun analyze(message: String, workspaceId: String = ""): SkillSelectionResult {
        val lowerMessage = message.lowercase()

        // Layer 1: @tag aliases (highest priority, confidence=1.0)
        for ((tag, route) in TAG_ROUTES) {
            if (message.contains(tag)) {
                logger.info("Intent routed via tag '{}': skills={}", tag, route.skills)
                return SkillSelectionResult(
                    skills = route.skills,
                    baselines = route.baselines,
                    confidence = 1.0,
                    reason = "Explicit tag: $tag",
                    mode = route.mode,
                    profileHint = route.profileHint
                )
            }
        }

        // Layer 2: keyword detection
        var bestRoute: KeywordRoute? = null
        var bestScore = 0
        var bestKeyword = ""

        for (route in KEYWORD_ROUTES) {
            var score = 0
            var matchedKeyword = ""
            for (keyword in route.keywords) {
                if (lowerMessage.contains(keyword.lowercase())) {
                    score++
                    if (matchedKeyword.isEmpty()) matchedKeyword = keyword
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestRoute = route
                bestKeyword = matchedKeyword
            }
        }

        if (bestRoute != null && bestScore > 0) {
            val confidence = (0.5 + 0.1 * bestScore).coerceIn(0.5, 0.95)
            val skills = buildSkillList(bestRoute.skills, lowerMessage)

            logger.info(
                "Intent routed via keyword '{}' (score={}, confidence={}): skills={}",
                bestKeyword, bestScore, confidence, skills
            )
            return SkillSelectionResult(
                skills = skills,
                baselines = emptyList(),
                confidence = confidence,
                reason = "Keyword: $bestKeyword (score=$bestScore)",
                mode = bestRoute.mode,
                profileHint = bestRoute.profileHint
            )
        }

        // Layer 3: default fallback
        val defaultSkills = buildSkillList(DEFAULT_SKILLS, lowerMessage)
        logger.debug("Using default fallback skills: {}", defaultSkills)
        return SkillSelectionResult(
            skills = defaultSkills,
            baselines = emptyList(),
            confidence = 0.3,
            reason = "Default fallback",
            mode = "default",
            profileHint = DEFAULT_PROFILE_HINT
        )
    }

    /**
     * Append tech-stack specific skills based on message content.
     */
    private fun buildSkillList(baseSkills: List<String>, lowerMessage: String): List<String> {
        val result = baseSkills.toMutableList()

        val hasKotlin = KOTLIN_INDICATORS.any { lowerMessage.contains(it) }
        val hasSpring = SPRING_INDICATORS.any { lowerMessage.contains(it) }

        if (hasKotlin && "kotlin-conventions" !in result) {
            result.add("kotlin-conventions")
        }
        if (hasSpring && "spring-boot-patterns" !in result) {
            result.add("spring-boot-patterns")
        }

        return result
    }

    private data class TagRoute(
        val skills: List<String>,
        val baselines: List<String>,
        val mode: String = "default",
        val profileHint: String
    )

    private data class KeywordRoute(
        val keywords: List<String>,
        val skills: List<String>,
        val confidence: Double,
        val profileHint: String,
        val mode: String = "default"
    )
}

/**
 * Result of routing a user message to a set of skills.
 */
data class SkillSelectionResult(
    val skills: List<String>,
    val baselines: List<String>,
    val confidence: Double,
    val reason: String,
    val mode: String = "default",
    /** Profile hint for backward-compatible memory loading (maps to a profile name) */
    val profileHint: String = "development-profile"
)
