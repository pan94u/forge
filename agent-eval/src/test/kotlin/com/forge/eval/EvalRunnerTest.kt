package com.forge.eval

import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.CompletionResult
import com.forge.adapter.model.ModelAdapter
import com.forge.adapter.model.StopReason
import com.forge.adapter.model.TokenUsage
import io.mockk.coEvery
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class EvalRunnerTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var evalSetsDir: File

    @BeforeEach
    fun setUp() {
        evalSetsDir = File(tempDir, "eval-sets")
        evalSetsDir.mkdirs()
    }

    // --- evaluateAssertion tests ---

    @Test
    fun `evaluateAssertion contains - passes when output contains expected`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion("contains", "hello", "Should contain hello", "say hello world")
        assertThat(result.passed).isTrue()
        assertThat(result.description).isEqualTo("Should contain hello")
    }

    @Test
    fun `evaluateAssertion contains - fails when output does not contain expected`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion("contains", "goodbye", "Should contain goodbye", "say hello world")
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `evaluateAssertion not_contains - passes when output does not contain expected`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion("not_contains", "error", "Should not contain error", "all good here")
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `evaluateAssertion not_contains - fails when output contains expected`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion("not_contains", "error", "Should not contain error", "there was an error")
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `evaluateAssertion matches_pattern - passes when regex matches`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion(
            "matches_pattern",
            """\d{3}-\d{4}""",
            "Should match phone pattern",
            "Call 555-1234 for info"
        )
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `evaluateAssertion matches_pattern - fails when regex does not match`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion(
            "matches_pattern",
            """\d{3}-\d{4}""",
            "Should match phone pattern",
            "No phone here"
        )
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `evaluateAssertion json_schema - passes for valid JSON output`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion(
            "json_schema",
            """{"type":"object"}""",
            "Should be valid JSON",
            """{"name":"test","value":42}"""
        )
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `evaluateAssertion json_schema - passes for JSON in markdown code block`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val output = """Here is the result:
```json
{"name":"test","value":42}
```
Done."""
        val result = runner.evaluateAssertion("json_schema", """{"type":"object"}""", "Should be JSON", output)
        assertThat(result.passed).isTrue()
    }

    @Test
    fun `evaluateAssertion json_schema - fails for invalid JSON`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion(
            "json_schema",
            """{"type":"object"}""",
            "Should be valid JSON",
            "This is not JSON at all"
        )
        assertThat(result.passed).isFalse()
    }

    @Test
    fun `evaluateAssertion semantic_similarity - passes when output contains expected`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion(
            "semantic_similarity",
            "expected meaning",
            "Semantic check",
            "this has expected meaning in the output"
        )
        assertThat(result.passed).isTrue()
        assertThat(result.actual).contains("Contains match")
    }

    @Test
    fun `evaluateAssertion semantic_similarity - fails when output lacks expected`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion(
            "semantic_similarity",
            "fibonacci sequence",
            "Semantic check",
            "the weather is sunny"
        )
        assertThat(result.passed).isFalse()
        assertThat(result.actual).contains("No semantic match")
    }

    @Test
    fun `evaluateAssertion unknown type - fails`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir)
        val result = runner.evaluateAssertion("unknown_type", "expected", "Unknown", "output")
        assertThat(result.passed).isFalse()
        assertThat(result.actual).contains("Unknown assertion type")
    }

    // --- runScenario without adapter (structure validation) ---

    @Test
    fun `runScenario without adapter validates structure`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir, modelAdapter = null)

        val scenario = EvalRunner.EvalScenario(
            id = "test-001",
            profile = "development",
            name = "Test scenario",
            description = "A test",
            prompt = "Do something",
            context = mapOf("framework" to "Spring"),
            assertions = listOf(
                mapOf("type" to "contains", "expected" to "result", "description" to "Has result"),
                mapOf("type" to "not_contains", "expected" to "error", "description" to "No error")
            ),
            qualityCriteria = mapOf("completeness" to 0.8),
            tags = listOf("smoke"),
            baselinePassRate = 0.8
        )

        val result = runner.runScenario(scenario)

        assertThat(result.passed).isTrue()
        assertThat(result.score).isEqualTo(1.0)
        assertThat(result.assertions).hasSize(2)
        assertThat(result.assertions[0].actual).contains("structure valid")
    }

    @Test
    fun `runScenario without adapter fails for invalid assertion type`() {
        val runner = EvalRunner(evalSetsDir = evalSetsDir, modelAdapter = null)

        val scenario = EvalRunner.EvalScenario(
            id = "test-002",
            profile = "development",
            name = "Bad scenario",
            description = "Invalid",
            prompt = "Do something",
            context = emptyMap(),
            assertions = listOf(
                mapOf("type" to "invalid_type", "expected" to "foo", "description" to "Bad type")
            ),
            qualityCriteria = emptyMap(),
            tags = emptyList(),
            baselinePassRate = 0.8
        )

        val result = runner.runScenario(scenario)

        assertThat(result.passed).isFalse()
        assertThat(result.score).isEqualTo(0.0)
    }

    // --- runScenario with mock adapter (real evaluation) ---

    @Test
    fun `runScenario with adapter calls model and evaluates assertions`() {
        val mockAdapter = mockk<ModelAdapter>()
        coEvery { mockAdapter.complete(any(), any()) } returns CompletionResult(
            content = "Here is the Spring Boot configuration for your service.",
            model = "claude-sonnet-4-20250514",
            usage = TokenUsage(inputTokens = 100, outputTokens = 50),
            stopReason = StopReason.END_TURN,
            latencyMs = 500
        )

        val runner = EvalRunner(evalSetsDir = evalSetsDir, modelAdapter = mockAdapter)

        val scenario = EvalRunner.EvalScenario(
            id = "eval-spring-001",
            profile = "development",
            name = "Spring config",
            description = "Test Spring config generation",
            prompt = "Generate a Spring Boot config",
            context = mapOf("framework" to "Spring Boot", "language" to "Kotlin"),
            assertions = listOf(
                mapOf("type" to "contains", "expected" to "Spring Boot", "description" to "Mentions Spring Boot"),
                mapOf("type" to "not_contains", "expected" to "Django", "description" to "No Django references"),
                mapOf("type" to "matches_pattern", "expected" to "(?i)config", "description" to "Mentions config")
            ),
            qualityCriteria = mapOf("completeness" to 0.9),
            tags = listOf("smoke", "spring"),
            baselinePassRate = 0.8
        )

        val result = runner.runScenario(scenario)

        assertThat(result.passed).isTrue()
        assertThat(result.score).isEqualTo(1.0)
        assertThat(result.assertions).hasSize(3)
        assertThat(result.assertions.all { it.passed }).isTrue()
    }

    @Test
    fun `runScenario with adapter handles failing assertions`() {
        val mockAdapter = mockk<ModelAdapter>()
        coEvery { mockAdapter.complete(any(), any()) } returns CompletionResult(
            content = "Use Django REST framework for this API.",
            model = "claude-sonnet-4-20250514",
            usage = TokenUsage(inputTokens = 50, outputTokens = 30),
            stopReason = StopReason.END_TURN,
            latencyMs = 300
        )

        val runner = EvalRunner(evalSetsDir = evalSetsDir, modelAdapter = mockAdapter)

        val scenario = EvalRunner.EvalScenario(
            id = "eval-spring-002",
            profile = "development",
            name = "Spring API",
            description = "Expected Spring but got Django",
            prompt = "Generate a Spring Boot REST API",
            context = mapOf("framework" to "Spring Boot"),
            assertions = listOf(
                mapOf("type" to "contains", "expected" to "Spring Boot", "description" to "Mentions Spring Boot"),
                mapOf("type" to "not_contains", "expected" to "Django", "description" to "No Django")
            ),
            qualityCriteria = emptyMap(),
            tags = emptyList(),
            baselinePassRate = 0.8
        )

        val result = runner.runScenario(scenario)

        assertThat(result.passed).isFalse()
        assertThat(result.score).isEqualTo(0.0) // both assertions fail
    }

    // --- discoverScenarios filtering ---

    @Test
    fun `discoverScenarios filters by profile`() {
        // Create two profile directories
        val devDir = File(evalSetsDir, "development-profile").also { it.mkdirs() }
        val testDir = File(evalSetsDir, "testing-profile").also { it.mkdirs() }

        File(devDir, "dev-001.yaml").writeText("""
            id: dev-001
            name: Dev scenario
            description: A dev test
            prompt: Do dev stuff
            assertions:
              - type: contains
                expected: result
                description: Has result
            tags:
              - smoke
        """.trimIndent())

        File(testDir, "test-001.yaml").writeText("""
            id: test-001
            name: Test scenario
            description: A test test
            prompt: Do test stuff
            assertions:
              - type: contains
                expected: result
                description: Has result
            tags:
              - regression
        """.trimIndent())

        val runner = EvalRunner(evalSetsDir = evalSetsDir)

        // All scenarios
        val all = runner.discoverScenarios()
        assertThat(all).hasSize(2)

        // Filter by development profile
        val devOnly = runner.discoverScenarios(profileFilter = "development")
        assertThat(devOnly).hasSize(1)
        assertThat(devOnly[0].profile).isEqualTo("development")

        // Filter by testing profile
        val testOnly = runner.discoverScenarios(profileFilter = "testing")
        assertThat(testOnly).hasSize(1)
        assertThat(testOnly[0].profile).isEqualTo("testing")
    }

    @Test
    fun `discoverScenarios filters by tag`() {
        val devDir = File(evalSetsDir, "development-profile").also { it.mkdirs() }

        File(devDir, "smoke-001.yaml").writeText("""
            id: smoke-001
            name: Smoke test
            prompt: Quick check
            assertions:
              - type: contains
                expected: ok
                description: Is ok
            tags:
              - smoke
        """.trimIndent())

        File(devDir, "regression-001.yaml").writeText("""
            id: regression-001
            name: Regression test
            prompt: Full check
            assertions:
              - type: contains
                expected: ok
                description: Is ok
            tags:
              - regression
        """.trimIndent())

        val runner = EvalRunner(evalSetsDir = evalSetsDir)

        val smokeOnly = runner.discoverScenarios(tagFilter = "smoke")
        assertThat(smokeOnly).hasSize(1)
        assertThat(smokeOnly[0].tags).contains("smoke")

        val regressionOnly = runner.discoverScenarios(tagFilter = "regression")
        assertThat(regressionOnly).hasSize(1)
        assertThat(regressionOnly[0].tags).contains("regression")
    }

    @Test
    fun `discoverScenarios returns empty for missing directory`() {
        val runner = EvalRunner(evalSetsDir = File(tempDir, "nonexistent"))
        val scenarios = runner.discoverScenarios()
        assertThat(scenarios).isEmpty()
    }
}
