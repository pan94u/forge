package com.forge.adapter.model

import org.slf4j.LoggerFactory

/**
 * Central registry for all model adapters.
 *
 * ModelRegistry manages multiple ModelAdapter instances and provides:
 * - Model discovery across all providers
 * - Adapter lookup by model ID or provider name
 * - Default adapter selection
 * - Aggregated health checks
 *
 * Usage:
 * ```kotlin
 * val registry = ModelRegistry(
 *     adapters = mapOf(
 *         "anthropic" to claudeAdapter,
 *         "dashscope" to qwenAdapter,
 *         "google" to geminiAdapter,
 *         "aws-bedrock" to bedrockAdapter
 *     ),
 *     defaultProvider = "anthropic"
 * )
 *
 * val adapter = registry.adapterForModel("qwen-plus") // returns QwenAdapter
 * val allModels = registry.allModels()                 // all models across providers
 * ```
 */
class ModelRegistry(
    private val adapters: Map<String, ModelAdapter>,
    private val defaultProvider: String = "anthropic"
) {

    private val logger = LoggerFactory.getLogger(ModelRegistry::class.java)

    // Build reverse index: modelId -> provider
    private val modelToProvider: Map<String, String> by lazy {
        adapters.flatMap { (provider, adapter) ->
            adapter.supportedModels().map { model -> model.id to provider }
        }.toMap()
    }

    /**
     * Get all models across all registered adapters.
     */
    fun allModels(): List<ModelInfo> {
        return adapters.values.flatMap { it.supportedModels() }
    }

    /**
     * Get models for a specific provider.
     */
    fun modelsForProvider(provider: String): List<ModelInfo> {
        return adapters[provider]?.supportedModels() ?: emptyList()
    }

    /**
     * Get all registered provider names.
     */
    fun providers(): Set<String> = adapters.keys

    /**
     * Get the adapter for a specific provider.
     */
    fun adapterForProvider(provider: String): ModelAdapter? = adapters[provider]

    /**
     * Get the adapter that supports a given model ID.
     * Falls back to the default adapter if the model is not found.
     */
    fun adapterForModel(modelId: String): ModelAdapter {
        val provider = modelToProvider[modelId]
        if (provider != null) {
            return adapters[provider]
                ?: throw ModelNotAvailableException("Provider '$provider' not configured")
        }

        logger.debug("Model '{}' not found in registry, falling back to default provider '{}'", modelId, defaultProvider)
        return defaultAdapter()
    }

    /**
     * Get the default adapter.
     */
    fun defaultAdapter(): ModelAdapter {
        return adapters[defaultProvider]
            ?: adapters.values.firstOrNull()
            ?: throw ModelAdapterException("No adapters registered in ModelRegistry")
    }

    /**
     * Run health checks on all adapters and return results per provider.
     */
    suspend fun healthCheckAll(): Map<String, Boolean> {
        return adapters.mapValues { (provider, adapter) ->
            try {
                adapter.healthCheck()
            } catch (e: Exception) {
                logger.warn("Health check failed for provider '{}': {}", provider, e.message)
                false
            }
        }
    }

    /**
     * Get summary info about the registry for diagnostics.
     */
    fun summary(): RegistrySummary {
        return RegistrySummary(
            providers = adapters.keys.toList(),
            totalModels = allModels().size,
            defaultProvider = defaultProvider,
            modelsByProvider = adapters.map { (provider, adapter) ->
                provider to adapter.supportedModels().size
            }.toMap()
        )
    }
}

/**
 * Summary of the ModelRegistry state.
 */
data class RegistrySummary(
    val providers: List<String>,
    val totalModels: Int,
    val defaultProvider: String,
    val modelsByProvider: Map<String, Int>
)
