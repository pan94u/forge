package com.forge.adapter.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory

/**
 * ModelAdapter implementation for AWS Bedrock.
 *
 * Provides access to Claude models hosted on AWS Bedrock, enabling enterprise
 * deployments that require data residency, VPC isolation, or AWS-native billing.
 *
 * Prerequisites:
 * - AWS SDK credentials configured (IAM role, env vars, or profile)
 * - Bedrock model access enabled in the target AWS region
 * - Required IAM permissions: bedrock:InvokeModel, bedrock:InvokeModelWithResponseStream
 *
 * @param region AWS region (e.g., "us-east-1", "eu-west-1")
 * @param profileName Optional AWS profile name for credential resolution
 */
class BedrockAdapter(
    private val region: String = System.getenv("AWS_REGION") ?: "us-east-1",
    private val profileName: String? = System.getenv("AWS_PROFILE")
) : ModelAdapter {

    private val logger = LoggerFactory.getLogger(BedrockAdapter::class.java)

    companion object {
        private const val BEDROCK_CLAUDE_OPUS = "anthropic.claude-opus-4-20250514-v1:0"
        private const val BEDROCK_CLAUDE_SONNET = "anthropic.claude-sonnet-4-20250514-v1:0"
        private const val BEDROCK_CLAUDE_HAIKU = "anthropic.claude-3-5-haiku-20241022-v1:0"

        val SUPPORTED_MODELS = listOf(
            ModelInfo(
                id = BEDROCK_CLAUDE_OPUS,
                displayName = "Claude Opus 4 (Bedrock)",
                provider = "aws-bedrock",
                contextWindow = 200_000,
                maxOutputTokens = 32_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.HIGH
            ),
            ModelInfo(
                id = BEDROCK_CLAUDE_SONNET,
                displayName = "Claude Sonnet 4 (Bedrock)",
                provider = "aws-bedrock",
                contextWindow = 200_000,
                maxOutputTokens = 16_000,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.MEDIUM
            ),
            ModelInfo(
                id = BEDROCK_CLAUDE_HAIKU,
                displayName = "Claude 3.5 Haiku (Bedrock)",
                provider = "aws-bedrock",
                contextWindow = 200_000,
                maxOutputTokens = 8_192,
                supportsStreaming = true,
                supportsVision = true,
                costTier = CostTier.LOW
            )
        )
    }

    override suspend fun complete(prompt: String, options: CompletionOptions): CompletionResult {
        val model = options.model ?: BEDROCK_CLAUDE_SONNET
        logger.info("Bedrock complete request: model={}, region={}", model, region)

        // AWS Bedrock integration requires the AWS SDK for Kotlin.
        // The implementation follows this pattern:
        //
        // 1. Build the Bedrock runtime client with region and credentials
        // 2. Construct the InvokeModelRequest with the Anthropic Messages API payload
        // 3. Parse the response following the same format as the direct API
        //
        // val bedrockClient = BedrockRuntimeClient { region = this@BedrockAdapter.region }
        // val request = InvokeModelRequest {
        //     modelId = model
        //     contentType = "application/json"
        //     accept = "application/json"
        //     body = buildRequestPayload(prompt, options)
        // }
        // val response = bedrockClient.invokeModel(request)
        // return parseResponse(response.body, model)

        throw ModelAdapterException(
            "BedrockAdapter is not yet fully implemented. " +
            "Add 'software.amazon.awssdk:bedrockruntime' to dependencies and " +
            "configure AWS credentials to enable Bedrock support.",
            retryable = false
        )
    }

    override suspend fun streamComplete(prompt: String, options: CompletionOptions): Flow<String> {
        val model = options.model ?: BEDROCK_CLAUDE_SONNET
        logger.info("Bedrock stream request: model={}, region={}", model, region)

        // Streaming implementation follows InvokeModelWithResponseStream API:
        //
        // val bedrockClient = BedrockRuntimeClient { region = this@BedrockAdapter.region }
        // val request = InvokeModelWithResponseStreamRequest {
        //     modelId = model
        //     contentType = "application/json"
        //     accept = "application/json"
        //     body = buildRequestPayload(prompt, options)
        // }
        // return flow {
        //     bedrockClient.invokeModelWithResponseStream(request) { response ->
        //         response.body?.collect { event ->
        //             when (event) {
        //                 is ResponseStream.Chunk -> emit(parseChunk(event))
        //             }
        //         }
        //     }
        // }

        return flow {
            throw ModelAdapterException(
                "BedrockAdapter streaming is not yet fully implemented.",
                retryable = false
            )
        }
    }

    override fun supportedModels(): List<ModelInfo> = SUPPORTED_MODELS

    override suspend fun healthCheck(): Boolean {
        return try {
            // Verify AWS credentials are available
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

            // In production, would make a lightweight ListFoundationModels call
            logger.info("Bedrock health check: region={}, credentialsAvailable={}", region, hasCredentials)
            true
        } catch (e: Exception) {
            logger.warn("Bedrock health check failed: {}", e.message)
            false
        }
    }
}
