package com.forge.webide.service.eval

import com.forge.webide.model.ContextReference
import com.forge.webide.service.ForgeAgentService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Forge 内部 Agent 适配器。
 * 桥接 ForgeAgentService 的回调模式到同步 AgentResponse。
 */
@Component
class ForgeAgentAdapter(
    private val forgeAgentService: ForgeAgentService
) : AgentAdapter {

    private val log = LoggerFactory.getLogger(ForgeAgentAdapter::class.java)

    override fun execute(input: String, config: AgentExecutionConfig): AgentResponse {
        val sessionId = "eval-${UUID.randomUUID()}"
        val workspaceId = config.workspaceId ?: "eval-workspace"
        val startMs = System.currentTimeMillis()

        val future = CompletableFuture<AgentResponse>()
        val transcript = mutableListOf<TranscriptTurn>()
        val contentBuilder = StringBuilder()

        transcript.add(TranscriptTurn(role = "user", content = input))

        forgeAgentService.streamMessage(
            sessionId = sessionId,
            message = input,
            contexts = emptyList(),
            workspaceId = workspaceId,
            modelId = config.modelName,
            onEvent = { event ->
                try {
                    val type = event["type"] as? String ?: return@streamMessage
                    when (type) {
                        "content" -> {
                            val text = event["text"] as? String ?: ""
                            contentBuilder.append(text)
                        }
                        "tool_use_start" -> {
                            val toolName = event["name"] as? String ?: "unknown"
                            transcript.add(TranscriptTurn(
                                role = "assistant",
                                content = "Calling tool: $toolName",
                                toolName = toolName
                            ))
                        }
                        "tool_use" -> {
                            val toolName = event["name"] as? String ?: "unknown"
                            val result = event["result"]?.toString() ?: ""
                            transcript.add(TranscriptTurn(
                                role = "tool",
                                content = result.take(2000),
                                toolName = toolName
                            ))
                        }
                    }
                } catch (e: Exception) {
                    log.warn("Error processing event in eval session {}: {}", sessionId, e.message)
                }
            },
            onComplete = { chatMessage ->
                val durationMs = System.currentTimeMillis() - startMs
                val output = contentBuilder.toString().ifBlank { chatMessage.content }
                transcript.add(TranscriptTurn(role = "assistant", content = output))
                future.complete(AgentResponse(
                    output = output,
                    transcript = transcript.toList(),
                    durationMs = durationMs,
                    success = true
                ))
            },
            onError = { exception ->
                val durationMs = System.currentTimeMillis() - startMs
                log.error("Eval session {} failed: {}", sessionId, exception.message)
                future.complete(AgentResponse(
                    output = contentBuilder.toString(),
                    transcript = transcript.toList(),
                    durationMs = durationMs,
                    success = false,
                    errorMessage = exception.message ?: "Unknown error"
                ))
            }
        )

        return try {
            future.get(config.timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            val durationMs = System.currentTimeMillis() - startMs
            log.error("Eval session {} timed out after {}ms", sessionId, config.timeoutMs)
            AgentResponse(
                output = contentBuilder.toString(),
                transcript = transcript.toList(),
                durationMs = durationMs,
                success = false,
                errorMessage = "Execution timed out after ${config.timeoutMs}ms"
            )
        }
    }
}
