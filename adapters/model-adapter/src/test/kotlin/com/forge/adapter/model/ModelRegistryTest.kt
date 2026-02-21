package com.forge.adapter.model

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ModelRegistryTest {

    private lateinit var claudeAdapter: ModelAdapter
    private lateinit var qwenAdapter: ModelAdapter
    private lateinit var geminiAdapter: ModelAdapter
    private lateinit var registry: ModelRegistry

    @BeforeEach
    fun setUp() {
        claudeAdapter = mockk<ModelAdapter> {
            every { supportedModels() } returns listOf(
                ModelInfo("claude-sonnet-4", "Claude Sonnet 4", "anthropic", 200_000, 16_000),
                ModelInfo("claude-haiku-3.5", "Claude Haiku 3.5", "anthropic", 200_000, 8_192)
            )
        }

        qwenAdapter = mockk<ModelAdapter> {
            every { supportedModels() } returns listOf(
                ModelInfo("qwen-plus", "Qwen Plus", "dashscope", 131_072, 8_192),
                ModelInfo("qwen-turbo", "Qwen Turbo", "dashscope", 131_072, 8_192)
            )
        }

        geminiAdapter = mockk<ModelAdapter> {
            every { supportedModels() } returns listOf(
                ModelInfo("gemini-2.0-flash", "Gemini 2.0 Flash", "google", 1_000_000, 8_192)
            )
        }

        registry = ModelRegistry(
            adapters = mapOf(
                "anthropic" to claudeAdapter,
                "dashscope" to qwenAdapter,
                "google" to geminiAdapter
            ),
            defaultProvider = "anthropic"
        )
    }

    @Test
    fun `allModels returns models from all providers`() {
        val models = registry.allModels()
        assertThat(models).hasSize(5)
    }

    @Test
    fun `modelsForProvider returns correct models`() {
        val qwenModels = registry.modelsForProvider("dashscope")
        assertThat(qwenModels).hasSize(2)
        assertThat(qwenModels.all { it.provider == "dashscope" }).isTrue()

        val unknownModels = registry.modelsForProvider("unknown")
        assertThat(unknownModels).isEmpty()
    }

    @Test
    fun `providers returns all registered provider names`() {
        val providers = registry.providers()
        assertThat(providers).containsExactlyInAnyOrder("anthropic", "dashscope", "google")
    }

    @Test
    fun `adapterForProvider returns correct adapter`() {
        assertThat(registry.adapterForProvider("anthropic")).isEqualTo(claudeAdapter)
        assertThat(registry.adapterForProvider("dashscope")).isEqualTo(qwenAdapter)
        assertThat(registry.adapterForProvider("google")).isEqualTo(geminiAdapter)
        assertThat(registry.adapterForProvider("unknown")).isNull()
    }

    @Test
    fun `adapterForModel routes to correct adapter by model ID`() {
        assertThat(registry.adapterForModel("claude-sonnet-4")).isEqualTo(claudeAdapter)
        assertThat(registry.adapterForModel("qwen-plus")).isEqualTo(qwenAdapter)
        assertThat(registry.adapterForModel("gemini-2.0-flash")).isEqualTo(geminiAdapter)
    }

    @Test
    fun `adapterForModel falls back to default for unknown model`() {
        assertThat(registry.adapterForModel("unknown-model")).isEqualTo(claudeAdapter)
    }

    @Test
    fun `defaultAdapter returns the default provider adapter`() {
        assertThat(registry.defaultAdapter()).isEqualTo(claudeAdapter)
    }

    @Test
    fun `defaultAdapter falls back to first adapter when default not found`() {
        val fallbackRegistry = ModelRegistry(
            adapters = mapOf("dashscope" to qwenAdapter),
            defaultProvider = "nonexistent"
        )
        assertThat(fallbackRegistry.defaultAdapter()).isEqualTo(qwenAdapter)
    }

    @Test
    fun `defaultAdapter throws when no adapters registered`() {
        val emptyRegistry = ModelRegistry(adapters = emptyMap())
        assertThatThrownBy { emptyRegistry.defaultAdapter() }
            .isInstanceOf(ModelAdapterException::class.java)
            .hasMessageContaining("No adapters registered")
    }

    @Test
    fun `healthCheckAll returns results per provider`() = runTest {
        coEvery { claudeAdapter.healthCheck() } returns true
        coEvery { qwenAdapter.healthCheck() } returns true
        coEvery { geminiAdapter.healthCheck() } returns false

        val results = registry.healthCheckAll()

        assertThat(results).hasSize(3)
        assertThat(results["anthropic"]).isTrue()
        assertThat(results["dashscope"]).isTrue()
        assertThat(results["google"]).isFalse()
    }

    @Test
    fun `healthCheckAll handles exceptions gracefully`() = runTest {
        coEvery { claudeAdapter.healthCheck() } returns true
        coEvery { qwenAdapter.healthCheck() } throws RuntimeException("Connection refused")
        coEvery { geminiAdapter.healthCheck() } returns true

        val results = registry.healthCheckAll()

        assertThat(results["anthropic"]).isTrue()
        assertThat(results["dashscope"]).isFalse() // Exception → false
        assertThat(results["google"]).isTrue()
    }

    @Test
    fun `summary returns correct registry info`() {
        val summary = registry.summary()

        assertThat(summary.providers).containsExactlyInAnyOrder("anthropic", "dashscope", "google")
        assertThat(summary.totalModels).isEqualTo(5)
        assertThat(summary.defaultProvider).isEqualTo("anthropic")
        assertThat(summary.modelsByProvider["anthropic"]).isEqualTo(2)
        assertThat(summary.modelsByProvider["dashscope"]).isEqualTo(2)
        assertThat(summary.modelsByProvider["google"]).isEqualTo(1)
    }
}
