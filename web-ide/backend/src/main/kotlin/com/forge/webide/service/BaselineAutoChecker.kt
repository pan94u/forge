package com.forge.webide.service

import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.Message
import com.forge.adapter.model.ModelAdapter
import com.forge.adapter.model.ToolDefinition
import com.forge.webide.service.skill.SkillLoader
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Runs baseline quality gate auto-checks after code generation.
 *
 * If baselines fail, injects failure context and re-enters the agentic loop
 * to fix issues. Supports up to [MAX_BASELINE_RETRIES] retry attempts.
 */
@Service
class BaselineAutoChecker(
    private val baselineService: BaselineService,
    private val agenticLoopOrchestrator: AgenticLoopOrchestrator,
    private val metricsService: MetricsService,
    private val skillLoader: SkillLoader
) {
    private val logger = LoggerFactory.getLogger(BaselineAutoChecker::class.java)

    companion object {
        const val MAX_BASELINE_RETRIES = 2
    }

    /**
     * Run baseline auto-check after code generation.
     * If baselines fail, inject failure context and run another agentic loop to fix.
     * Max [MAX_BASELINE_RETRIES] retry attempts.
     */
    fun runBaselineAutoCheck(
        result: AgenticResult,
        promptResult: DynamicPromptResult,
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>,
        workspaceId: String,
        onEvent: (Map<String, Any?>) -> Unit,
        adapter: ModelAdapter? = null
    ): AgenticResult {
        // Determine which baselines to run from the profile
        val profile = skillLoader.loadProfile(promptResult.activeProfile)
        val baselineNames = (profile?.baselines ?: emptyList()).ifEmpty {
            listOf("code-style-baseline", "security-baseline")
        }

        var currentResult = result
        for (attempt in 1..MAX_BASELINE_RETRIES) {
            logger.info("Baseline auto-check attempt {}/{}", attempt, MAX_BASELINE_RETRIES)
            emitSubStep(onEvent, "运行底线检查 (${attempt}/$MAX_BASELINE_RETRIES): ${baselineNames.joinToString()}")

            onEvent(mapOf(
                "type" to "baseline_check",
                "status" to "running",
                "attempt" to attempt,
                "baselines" to baselineNames
            ))

            val report = try {
                baselineService.runBaselines(baselineNames).also { r ->
                    r.results.forEach { br ->
                        metricsService.recordBaselineResult(br.name, br.status == "PASS")
                    }
                }
            } catch (e: Exception) {
                logger.warn("Baseline execution failed: {}", e.message)
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "error",
                    "attempt" to attempt,
                    "message" to "Baseline execution error: ${e.message}"
                ))
                // Don't block on baseline execution errors
                return currentResult
            }

            if (report.allPassed) {
                logger.info("Baselines passed on attempt {}", attempt)
                emitSubStep(onEvent, "底线检查全部通过 ✅")
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "passed",
                    "attempt" to attempt,
                    "summary" to report.summary
                ))
                return currentResult
            }

            logger.info("Baselines failed on attempt {}: {}", attempt, report.summary)
            emitSubStep(onEvent, "底线检查失败 ❌: ${report.summary.take(100)}")
            onEvent(mapOf(
                "type" to "baseline_check",
                "status" to "failed",
                "attempt" to attempt,
                "summary" to report.summary
            ))

            if (attempt >= MAX_BASELINE_RETRIES) {
                // Max retries reached, report failure and return current result
                logger.warn("Baselines still failing after {} attempts, returning result with warning", MAX_BASELINE_RETRIES)
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "exhausted",
                    "summary" to report.summary
                ))
                return currentResult
            }

            // Loop back to Observe: inject baseline failure and re-run agentic loop
            onEvent(mapOf("type" to "ooda_phase", "phase" to "observe"))
            metricsService.recordOodaPhase("observe")

            val failureContext = report.results
                .filter { it.status == "FAIL" || it.status == "ERROR" }
                .joinToString("\n") { r ->
                    "- ${r.name}: ${r.output.take(500)}"
                }

            val fixMessages = messages.toMutableList()
            fixMessages.add(Message(
                role = Message.Role.ASSISTANT,
                content = currentResult.content
            ))
            fixMessages.add(Message(
                role = Message.Role.USER,
                content = """底线检查失败，请修复以下问题后重新提交代码：

$failureContext

请分析失败原因，修改相关文件使底线检查通过。修改完成后不要再调用底线检查工具。"""
            ))

            // Run another agentic loop to fix (with rate-limit protection)
            try {
                currentResult = runBlocking {
                    agenticLoopOrchestrator.agenticStream(
                        messages = fixMessages,
                        options = options,
                        tools = tools,
                        onEvent = onEvent,
                        workspaceId = workspaceId,
                        adapter = adapter
                    )
                }
            } catch (e: Exception) {
                // Rate limit or other API error during baseline fix -- skip fix, return what we have
                logger.warn("Baseline fix loop aborted ({}): {}", e.javaClass.simpleName, e.message?.take(100))
                emitSubStep(onEvent, "底线修复跳过（API 限制），返回当前结果")
                onEvent(mapOf(
                    "type" to "baseline_check",
                    "status" to "exhausted",
                    "summary" to "Baseline fix skipped due to: ${e.message?.take(80)}"
                ))
                return currentResult
            }
        }

        return currentResult
    }
}
