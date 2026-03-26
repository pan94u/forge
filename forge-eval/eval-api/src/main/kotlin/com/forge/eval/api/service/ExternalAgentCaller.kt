package com.forge.eval.api.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.eval.engine.TrialOutput
import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.UUID

/**
 * 泛化的外部 Agent 调用器。
 * 支持 SSE（流式）和 REST（同步）两种协议，通过 AgentEndpointConfig 配置。
 */
@Component
class ExternalAgentCaller(
    private val objectMapper: ObjectMapper
) {

    private val log = LoggerFactory.getLogger(ExternalAgentCaller::class.java)

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)     // 强制 HTTP/1.1，避免 HTTP/2 协商失败
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun call(endpoint: String, config: AgentEndpointConfig?, task: EvalTask): TrialOutput {
        val effectiveConfig = config ?: AgentEndpointConfig()
        val startMs = System.currentTimeMillis()

        log.info("[Eval→Agent] {} {} task='{}' timeout={}ms",
            effectiveConfig.protocol, endpoint, task.name, effectiveConfig.timeoutMs)

        return try {
            when (effectiveConfig.protocol) {
                AgentProtocol.SSE -> callSSE(endpoint, effectiveConfig, task, startMs)
                AgentProtocol.REST -> callREST(endpoint, effectiveConfig, task, startMs)
            }
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            log.error("[Eval→Agent] {} → FAILED: {}", endpoint, e.message)
            TrialOutput(
                output = "",
                error = "Agent call failed: ${e.message}",
                // durationMs tracked via EvalEngine
            )
        }
    }

    /**
     * SSE 协议：POST 到端点，读取 SSE 事件流，收集 text content 和 tool_use 事件。
     * 兼容 Anthropic 风格 SSE（CIMC / Synapse 等基于 @synapse/agent-core 的 Agent）。
     */
    private fun callSSE(endpoint: String, config: AgentEndpointConfig, task: EvalTask, startMs: Long): TrialOutput {
        val requestBody = buildRequestBody(config, task)
        val request = buildHttpRequest(endpoint, config, requestBody, streaming = true)

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val durationMs = System.currentTimeMillis() - startMs

        if (response.statusCode() !in 200..299) {
            val body = response.body().bufferedReader().readText()
            log.error("[Eval→Agent] SSE {} → {}: {}", endpoint, response.statusCode(), body.take(300))
            return TrialOutput(
                output = "",
                error = "Agent returned ${response.statusCode()}: ${body.take(200)}",
                // durationMs tracked via EvalEngine
            )
        }

        val contentBuilder = StringBuilder()
        val turns = mutableListOf<TranscriptTurn>()
        val toolCalls = mutableListOf<ToolCallInfo>()

        turns.add(TranscriptTurn(role = "user", content = task.prompt))

        response.body().bufferedReader().use { reader ->
            parseSSEStream(reader, contentBuilder, turns, toolCalls)
        }

        val finalDuration = System.currentTimeMillis() - startMs
        val output = contentBuilder.toString()

        turns.add(TranscriptTurn(role = "assistant", content = output))

        log.info("[Eval→Agent] SSE {} completed in {}ms, output={}chars, tools={}",
            endpoint, finalDuration, output.length, toolCalls.size)

        return TrialOutput(
            output = output,
            transcript = EvalTranscript(
                source = TranscriptSource.EXTERNAL,
                turns = turns,
                toolCallSummary = toolCalls,
                metadata = mapOf("endpoint" to endpoint, "protocol" to "SSE", "durationMs" to finalDuration.toString())
            ),
        )
    }

    /**
     * REST 协议：POST 到端点，同步等待 JSON 响应。
     */
    private fun callREST(endpoint: String, config: AgentEndpointConfig, task: EvalTask, startMs: Long): TrialOutput {
        val requestBody = buildRequestBody(config, task)
        val request = buildHttpRequest(endpoint, config, requestBody, streaming = false)

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        val durationMs = System.currentTimeMillis() - startMs

        if (response.statusCode() !in 200..299) {
            log.error("[Eval→Agent] REST {} → {}: {}", endpoint, response.statusCode(), response.body().take(300))
            return TrialOutput(
                output = "",
                error = "Agent returned ${response.statusCode()}: ${response.body().take(200)}",
                // durationMs tracked via EvalEngine
            )
        }

        val tree = objectMapper.readTree(response.body())
        val output = resolveJsonPath(tree, config.outputJsonPath) ?: ""

        val transcript = if (tree.has("transcript")) {
            val turns = tree.get("transcript").map { turn ->
                TranscriptTurn(
                    role = turn.get("role")?.asText() ?: "unknown",
                    content = turn.get("content")?.asText() ?: ""
                )
            }
            EvalTranscript(
                source = TranscriptSource.EXTERNAL,
                turns = turns,
                metadata = mapOf("endpoint" to endpoint, "protocol" to "REST")
            )
        } else {
            EvalTranscript(
                source = TranscriptSource.EXTERNAL,
                turns = listOf(
                    TranscriptTurn(role = "user", content = task.prompt),
                    TranscriptTurn(role = "assistant", content = output)
                ),
                metadata = mapOf("endpoint" to endpoint, "protocol" to "REST")
            )
        }

        log.info("[Eval→Agent] REST {} completed in {}ms, output={}chars", endpoint, durationMs, output.length)

        return TrialOutput(output = output, transcript = transcript)
    }

    // ── SSE 解析 ───────────────────────────────────────────────────

    private fun parseSSEStream(
        reader: BufferedReader,
        contentBuilder: StringBuilder,
        turns: MutableList<TranscriptTurn>,
        toolCalls: MutableList<ToolCallInfo>
    ) {
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val currentLine = line ?: continue
            if (!currentLine.startsWith("data:")) continue
            val data = currentLine.removePrefix("data:").trim()
            if (data == "[DONE]" || data.isEmpty()) continue

            try {
                val event = objectMapper.readTree(data)
                val type = event.get("type")?.asText() ?: continue

                when (type) {
                    // 文本内容块
                    "content", "text", "content_block_delta" -> {
                        val text = event.get("text")?.asText()
                            ?: event.get("delta")?.get("text")?.asText()
                            ?: event.get("content")?.asText()
                        if (text != null) contentBuilder.append(text)
                    }
                    // 工具调用
                    "tool_use", "tool_call", "tool_use_start" -> {
                        val toolName = event.get("name")?.asText()
                            ?: event.get("toolName")?.asText()
                            ?: "unknown"
                        val args = event.get("input")?.toString()
                            ?: event.get("arguments")?.toString()
                            ?: event.get("toolInput")?.toString()
                            ?: "{}"
                        val argsMap: Map<String, Any> = try {
                            objectMapper.readValue(args, Map::class.java) as Map<String, Any>
                        } catch (_: Exception) { mapOf("raw" to args) }
                        toolCalls.add(ToolCallInfo(toolName = toolName, arguments = argsMap))
                        turns.add(TranscriptTurn(
                            role = "assistant",
                            content = "Tool: $toolName($args)",
                            toolCalls = listOf(ToolCallInfo(toolName = toolName, arguments = argsMap))
                        ))
                    }
                    // 工具结果
                    "tool_result" -> {
                        val result = event.get("result")?.asText()
                            ?: event.get("content")?.asText() ?: ""
                        turns.add(TranscriptTurn(role = "tool", content = result.take(2000)))
                    }
                    // 错误
                    "error" -> {
                        val msg = event.get("message")?.asText() ?: "unknown error"
                        log.warn("[Eval→Agent] SSE error event: {}", msg)
                    }
                }
            } catch (e: Exception) {
                log.debug("[Eval→Agent] Skipping unparseable SSE data: {}", data.take(100))
            }
        }
    }

    // ── 请求构建 ───────────────────────────────────────────────────

    private fun buildRequestBody(config: AgentEndpointConfig, task: EvalTask): String {
        val template = config.requestTemplate
        if (!template.isNullOrBlank()) {
            return template
                .replace("{{prompt}}", task.prompt)
                .replace("{{taskId}}", task.id.toString())
                .replace("{{taskName}}", task.name)
        }

        // 默认格式：兼容 Anthropic 风格 Agent（CIMC / Synapse）
        return when (config.protocol) {
            AgentProtocol.SSE -> objectMapper.writeValueAsString(mapOf(
                "messages" to listOf(mapOf("role" to "user", "content" to task.prompt)),
                "sessionId" to "eval-${UUID.randomUUID()}"
            ))
            AgentProtocol.REST -> objectMapper.writeValueAsString(mapOf(
                "prompt" to task.prompt
            ))
        }
    }

    private fun buildHttpRequest(
        endpoint: String,
        config: AgentEndpointConfig,
        body: String,
        streaming: Boolean
    ): HttpRequest {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofMillis(config.timeoutMs))
            .POST(HttpRequest.BodyPublishers.ofString(body))

        if (streaming) {
            builder.header("Accept", "text/event-stream")
        }

        config.headers.forEach { (k, v) -> builder.header(k, v) }

        return builder.build()
    }

    // ── JSON path 解析 ─────────────────────────────────────────────

    private fun resolveJsonPath(node: com.fasterxml.jackson.databind.JsonNode, path: String): String? {
        var current: com.fasterxml.jackson.databind.JsonNode? = node
        for (segment in path.split(".")) {
            current = current?.get(segment) ?: return null
        }
        return current?.asText()
    }
}
