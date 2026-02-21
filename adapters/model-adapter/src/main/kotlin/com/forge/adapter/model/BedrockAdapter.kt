package com.forge.adapter.model

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.document.Document
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest
import software.amazon.awssdk.services.bedrockruntime.model.InferenceConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.Tool
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock

/**
 * ModelAdapter implementation for AWS Bedrock using the Converse API.
 *
 * Uses the synchronous [BedrockRuntimeClient] with the Converse API for a unified
 * interface across Bedrock-hosted models. Streaming methods emit events from the
 * complete response (pseudo-streaming) to avoid async client complexity.
 *
 * Prerequisites:
 * - AWS SDK credentials configured (IAM role, env vars, or profile)
 * - Bedrock model access enabled in the target AWS region
 * - Required IAM permissions: bedrock:InvokeModel
 *
 * @param region AWS region (e.g., "us-east-1", "eu-west-1")
 * @param profileName Optional AWS profile name for credential resolution
 */
class BedrockAdapter(
    private val region: String = System.getenv("AWS_REGION") ?: "us-east-1",
    private val profileName: String? = System.getenv("AWS_PROFILE"),
    private val customModels: List<ModelInfo>? = null
) : ModelAdapter {

    private val logger = LoggerFactory.getLogger(BedrockAdapter::class.java)
    private val gson = Gson()

    private val bedrockClient: BedrockRuntimeClient by lazy {
        BedrockRuntimeClient.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()
    }

    companion object {
        private const val BEDROCK_CLAUDE_OPUS = "anthropic.claude-opus-4-6-v1"
        private const val BEDROCK_CLAUDE_SONNET = "anthropic.claude-sonnet-4-6"
        private const val BEDROCK_CLAUDE_HAIKU = "anthropic.claude-haiku-4-5-20251001-v1:0"

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                id = BEDROCK_CLAUDE_OPUS,
                displayName = "Claude Opus 4.6 (Bedrock)",
                provider = "aws-bedrock",
                contextWindow = 200_000,
                maxOutputTokens = 128_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.HIGH
            ),
            ModelInfo(
                id = BEDROCK_CLAUDE_SONNET,
                displayName = "Claude Sonnet 4.6 (Bedrock)",
                provider = "aws-bedrock",
                contextWindow = 200_000,
                maxOutputTokens = 64_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.MEDIUM
            ),
            ModelInfo(
                id = BEDROCK_CLAUDE_HAIKU,
                displayName = "Claude Haiku 4.5 (Bedrock)",
                provider = "aws-bedrock",
                contextWindow = 200_000,
                maxOutputTokens = 64_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.LOW
            )
        )
    }

    override suspend fun complete(prompt: String, options: CompletionOptions): CompletionResult {
        val model = options.model ?: BEDROCK_CLAUDE_SONNET
        logger.info("Bedrock complete request: model={}, region={}", model, region)

        val startTime = System.currentTimeMillis()

        return withContext(Dispatchers.IO) {
            try {
                val response = callConverse(
                    model = model,
                    messages = listOf(Message(Message.Role.USER, prompt)),
                    options = options,
                    tools = emptyList()
                )
                val latency = System.currentTimeMillis() - startTime

                val content = response.output().message().content()
                    .mapNotNull { it.text() }
                    .joinToString("")

                val usage = response.usage()

                CompletionResult(
                    content = content,
                    model = model,
                    usage = TokenUsage(
                        inputTokens = usage?.inputTokens() ?: 0,
                        outputTokens = usage?.outputTokens() ?: 0
                    ),
                    stopReason = mapStopReason(response.stopReasonAsString()),
                    latencyMs = latency
                )
            } catch (e: software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException) {
                throw AuthenticationException("Bedrock access denied: ${e.message}", e)
            } catch (e: software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException) {
                throw RateLimitException("Bedrock throttled: ${e.message}", cause = e)
            } catch (e: software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException) {
                throw ModelNotAvailableException("Bedrock model not found: ${e.message}", e)
            } catch (e: ModelAdapterException) {
                throw e
            } catch (e: Exception) {
                throw ModelAdapterException("Bedrock error: ${e.message}", e)
            }
        }
    }

    override suspend fun streamComplete(prompt: String, options: CompletionOptions): Flow<String> {
        val model = options.model ?: BEDROCK_CLAUDE_SONNET
        logger.info("Bedrock stream request: model={}, region={}", model, region)

        return flow {
            val result = complete(prompt, options)
            emit(result.content)
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun streamWithTools(
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>
    ): Flow<StreamEvent> {
        val model = options.model ?: BEDROCK_CLAUDE_SONNET
        logger.info("Bedrock streamWithTools: model={}, messages={}, tools={}", model, messages.size, tools.size)

        return flow {
            try {
                val response = withContext(Dispatchers.IO) {
                    callConverse(model, messages, options, tools)
                }

                emit(StreamEvent.MessageStart("", model))

                val contentBlocks = response.output().message().content()
                var blockIndex = 0

                for (block in contentBlocks) {
                    val text = block.text()
                    if (text != null) {
                        emit(StreamEvent.ContentDelta(text))
                    }

                    val toolUse = block.toolUse()
                    if (toolUse != null) {
                        emit(StreamEvent.ToolUseStart(blockIndex, toolUse.toolUseId(), toolUse.name()))
                        val inputJson = documentToJson(toolUse.input())
                        emit(StreamEvent.ToolInputDelta(inputJson))
                        emit(StreamEvent.ToolUseEnd(blockIndex))
                    }

                    blockIndex++
                }

                emit(StreamEvent.MessageDelta(mapStopReason(response.stopReasonAsString())))
                emit(StreamEvent.MessageStop)
            } catch (e: software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException) {
                throw AuthenticationException("Bedrock access denied: ${e.message}", e)
            } catch (e: software.amazon.awssdk.services.bedrockruntime.model.ThrottlingException) {
                throw RateLimitException("Bedrock throttled: ${e.message}", cause = e)
            } catch (e: software.amazon.awssdk.services.bedrockruntime.model.ResourceNotFoundException) {
                throw ModelNotAvailableException("Bedrock model not found: ${e.message}", e)
            } catch (e: ModelAdapterException) {
                throw e
            } catch (e: Exception) {
                throw ModelAdapterException("Bedrock streaming error: ${e.message}", e)
            }
        }
    }

    override fun supportedModels(): List<ModelInfo> = customModels ?: SUPPORTED_MODELS

    override suspend fun healthCheck(): Boolean {
        return try {
            val hasRegion = region.isNotBlank()
            val hasCredentials = System.getenv("AWS_ACCESS_KEY_ID")?.isNotBlank() == true ||
                    System.getenv("AWS_PROFILE")?.isNotBlank() == true ||
                    profileName?.isNotBlank() == true

            if (!hasRegion) {
                logger.warn("AWS_REGION not set for Bedrock adapter")
                return false
            }
            if (!hasCredentials) {
                logger.warn("No AWS credentials found for Bedrock adapter")
                return false
            }

            logger.info("Bedrock health check: region={}, credentialsAvailable={}", region, hasCredentials)
            true
        } catch (e: Exception) {
            logger.warn("Bedrock health check failed: {}", e.message)
            false
        }
    }

    // ---- Internal helpers ----

    private fun callConverse(
        model: String,
        messages: List<Message>,
        options: CompletionOptions,
        tools: List<ToolDefinition>
    ): software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse {
        val requestBuilder = ConverseRequest.builder()
            .modelId(model)
            .messages(buildConverseMessages(messages))
            .inferenceConfig(buildInferenceConfig(options))

        if (options.systemPrompt != null) {
            requestBuilder.system(
                SystemContentBlock.builder().text(options.systemPrompt).build()
            )
        }

        if (tools.isNotEmpty()) {
            requestBuilder.toolConfig(buildToolConfig(tools))
        }

        return bedrockClient.converse(requestBuilder.build())
    }

    private fun buildConverseMessages(
        messages: List<Message>
    ): List<software.amazon.awssdk.services.bedrockruntime.model.Message> {
        return messages.mapNotNull { msg ->
            when (msg.role) {
                Message.Role.USER -> {
                    val contentBlocks = mutableListOf<ContentBlock>()
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        for (result in msg.toolResults) {
                            contentBlocks.add(
                                ContentBlock.builder()
                                    .toolResult(
                                        ToolResultBlock.builder()
                                            .toolUseId(result.toolUseId)
                                            .content(
                                                ToolResultContentBlock.builder()
                                                    .text(result.content)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    } else {
                        contentBlocks.add(ContentBlock.builder().text(msg.content).build())
                    }
                    software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(ConversationRole.USER)
                        .content(contentBlocks)
                        .build()
                }

                Message.Role.ASSISTANT -> {
                    val contentBlocks = mutableListOf<ContentBlock>()
                    if (msg.content.isNotBlank()) {
                        contentBlocks.add(ContentBlock.builder().text(msg.content).build())
                    }
                    if (msg.toolUses != null) {
                        for (toolUse in msg.toolUses) {
                            contentBlocks.add(
                                ContentBlock.builder()
                                    .toolUse(
                                        ToolUseBlock.builder()
                                            .toolUseId(toolUse.id)
                                            .name(toolUse.name)
                                            .input(mapToDocument(toolUse.input))
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                    software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(ConversationRole.ASSISTANT)
                        .content(contentBlocks)
                        .build()
                }

                Message.Role.TOOL -> {
                    val contentBlocks = mutableListOf<ContentBlock>()
                    if (msg.toolResults != null && msg.toolResults.isNotEmpty()) {
                        for (result in msg.toolResults) {
                            contentBlocks.add(
                                ContentBlock.builder()
                                    .toolResult(
                                        ToolResultBlock.builder()
                                            .toolUseId(result.toolUseId)
                                            .content(
                                                ToolResultContentBlock.builder()
                                                    .text(result.content)
                                                    .build()
                                            )
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    } else {
                        contentBlocks.add(ContentBlock.builder().text(msg.content).build())
                    }
                    software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                        .role(ConversationRole.USER)
                        .content(contentBlocks)
                        .build()
                }

                Message.Role.SYSTEM -> null // Handled via system parameter
            }
        }
    }

    private fun buildInferenceConfig(options: CompletionOptions): InferenceConfiguration {
        val builder = InferenceConfiguration.builder()
            .maxTokens(options.maxTokens)

        if (options.temperature != 0.7) {
            builder.temperature(options.temperature.toFloat())
        }
        if (options.topP != 1.0) {
            builder.topP(options.topP.toFloat())
        }
        if (options.stopSequences.isNotEmpty()) {
            builder.stopSequences(options.stopSequences)
        }

        return builder.build()
    }

    private fun buildToolConfig(tools: List<ToolDefinition>): ToolConfiguration {
        val sdkTools = tools.map { tool ->
            Tool.builder()
                .toolSpec(
                    ToolSpecification.builder()
                        .name(tool.name)
                        .description(tool.description)
                        .inputSchema(
                            ToolInputSchema.builder()
                                .json(mapToDocument(tool.inputSchema))
                                .build()
                        )
                        .build()
                )
                .build()
        }
        return ToolConfiguration.builder().tools(sdkTools).build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun mapToDocument(map: Map<String, Any?>): Document {
        val builder = Document.mapBuilder()
        for ((key, value) in map) {
            when (value) {
                is String -> builder.putString(key, value)
                is Int -> builder.putNumber(key, value)
                is Long -> builder.putNumber(key, value)
                is Double -> builder.putNumber(key, value)
                is Float -> builder.putNumber(key, value)
                is Boolean -> builder.putBoolean(key, value)
                is Map<*, *> -> builder.putDocument(key, mapToDocument(value as Map<String, Any?>))
                is List<*> -> builder.putList(key, listToDocumentList(value))
                null -> builder.putNull(key)
            }
        }
        return builder.build()
    }

    @Suppress("UNCHECKED_CAST")
    private fun listToDocumentList(list: List<*>): List<Document> {
        return list.map { item ->
            when (item) {
                is String -> Document.fromString(item)
                is Int -> Document.fromNumber(item)
                is Long -> Document.fromNumber(item)
                is Double -> Document.fromNumber(item)
                is Float -> Document.fromNumber(item)
                is Boolean -> Document.fromBoolean(item)
                is Map<*, *> -> mapToDocument(item as Map<String, Any?>)
                is List<*> -> Document.fromList(listToDocumentList(item))
                null -> Document.fromNull()
                else -> Document.fromString(item.toString())
            }
        }
    }

    private fun documentToJson(doc: Document): String {
        return gson.toJson(documentToMap(doc))
    }

    private fun documentToMap(doc: Document): Any? {
        return when {
            doc.isMap -> doc.asMap().mapValues { documentToMap(it.value) }
            doc.isList -> doc.asList().map { documentToMap(it) }
            doc.isString -> doc.asString()
            doc.isNumber -> doc.asNumber().stringValue().let { s ->
                s.toIntOrNull() ?: s.toLongOrNull() ?: s.toDoubleOrNull() ?: s
            }
            doc.isBoolean -> doc.asBoolean()
            doc.isNull -> null
            else -> doc.toString()
        }
    }

    private fun mapStopReason(reason: String?): StopReason {
        return when (reason?.lowercase()) {
            "end_turn" -> StopReason.END_TURN
            "stop_sequence" -> StopReason.STOP_SEQUENCE
            "max_tokens" -> StopReason.MAX_TOKENS
            "tool_use" -> StopReason.TOOL_USE
            else -> StopReason.END_TURN
        }
    }
}
