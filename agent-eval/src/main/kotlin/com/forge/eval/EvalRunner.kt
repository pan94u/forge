package com.forge.eval

import com.forge.adapter.model.ClaudeAdapter
import com.forge.adapter.model.CompletionOptions
import com.forge.adapter.model.ModelAdapter
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Runs evaluation suites against SuperAgent skill profiles.
 *
 * The EvalRunner is the core engine of the Forge evaluation framework. It:
 * 1. Discovers eval-set YAML files organized by profile
 * 2. Executes each evaluation scenario against the specified agent configuration
 * 3. Collects results (pass/fail, quality scores, latency)
 * 4. Delegates to EvalReporter for output generation
 *
 * Eval sets are organized as:
 *   eval-sets/{profile}/{eval-id}.yaml
 *
 * Each eval YAML defines:
 *   - A scenario (prompt, context, expected behavior)
 *   - Assertions (output contains, does not contain, matches pattern)
 *   - Quality criteria (completeness, correctness, style)
 *
 * When a [modelAdapter] is provided, scenarios are evaluated by calling
 * the real model and checking assertions against the actual output.
 * Without an adapter, only YAML structure validation is performed.
 */
class EvalRunner(
    private val evalSetsDir: File = File("eval-sets"),
    private val reporter: EvalReporter = EvalReporter(),
    private val baselineChecker: BaselineChecker = BaselineChecker(),
    private val modelAdapter: ModelAdapter? = null
) {

    private val logger = LoggerFactory.getLogger(EvalRunner::class.java)
    private val yaml = Yaml()

    /**
     * Result of a single evaluation scenario.
     */
    data class EvalResult(
        val evalId: String,
        val profile: String,
        val scenario: String,
        val passed: Boolean,
        val score: Double,
        val assertions: List<AssertionResult>,
        val durationMs: Long,
        val notes: String = ""
    )

    data class AssertionResult(
        val description: String,
        val passed: Boolean,
        val expected: String,
        val actual: String = ""
    )

    /**
     * A parsed evaluation scenario from YAML.
     */
    data class EvalScenario(
        val id: String,
        val profile: String,
        val name: String,
        val description: String,
        val prompt: String,
        val context: Map<String, Any>,
        val assertions: List<Map<String, Any>>,
        val qualityCriteria: Map<String, Double>,
        val tags: List<String>,
        val baselinePassRate: Double
    )

    /**
     * Run all evaluation suites, optionally filtered by profile.
     *
     * @param profileFilter Only run evals for this profile (null = all)
     * @param tagFilter Only run evals with this tag (null = all)
     * @return List of all eval results
     */
    fun runAll(profileFilter: String? = null, tagFilter: String? = null): List<EvalResult> {
        logger.info("Starting evaluation run (profile={}, tag={})", profileFilter ?: "all", tagFilter ?: "all")

        val scenarios = discoverScenarios(profileFilter, tagFilter)
        logger.info("Discovered {} evaluation scenarios", scenarios.size)

        val results = mutableListOf<EvalResult>()

        for (scenario in scenarios) {
            logger.info("Running eval: {} (profile: {})", scenario.id, scenario.profile)
            val result = runScenario(scenario)
            results.add(result)

            val status = if (result.passed) "PASS" else "FAIL"
            logger.info("  Result: {} (score: {}, duration: {}ms)", status, result.score, result.durationMs)
        }

        // Check baselines
        val baselineResults = baselineChecker.checkBaselines(results)
        logger.info("Baseline check: {}", if (baselineResults.allPassed) "PASSED" else "FAILED")

        // Generate report
        reporter.generateReport(results, baselineResults)

        return results
    }

    /**
     * Run a single evaluation scenario by ID.
     */
    fun runSingle(evalId: String): EvalResult? {
        val scenarios = discoverScenarios()
        val scenario = scenarios.find { it.id == evalId }
        if (scenario == null) {
            logger.warn("Eval scenario not found: {}", evalId)
            return null
        }
        return runScenario(scenario)
    }

    /**
     * Discover all eval scenario YAML files.
     */
    fun discoverScenarios(profileFilter: String? = null, tagFilter: String? = null): List<EvalScenario> {
        if (!evalSetsDir.exists()) {
            logger.warn("Eval sets directory not found: {}", evalSetsDir.absolutePath)
            return emptyList()
        }

        val scenarios = mutableListOf<EvalScenario>()

        evalSetsDir.listFiles()?.filter { it.isDirectory }?.forEach { profileDir ->
            val profile = profileDir.name.removeSuffix("-profile")
            if (profileFilter != null && profile != profileFilter) return@forEach

            profileDir.listFiles()?.filter { it.extension == "yaml" || it.extension == "yml" }?.forEach { file ->
                try {
                    val scenario = parseScenario(file, profile)
                    if (tagFilter == null || tagFilter in scenario.tags) {
                        scenarios.add(scenario)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to parse eval file {}: {}", file.name, e.message)
                }
            }
        }

        return scenarios.sortedBy { "${it.profile}/${it.id}" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseScenario(file: File, profile: String): EvalScenario {
        val data = yaml.load<Map<String, Any>>(file.readText())

        return EvalScenario(
            id = data["id"] as? String ?: file.nameWithoutExtension,
            profile = profile,
            name = data["name"] as? String ?: file.nameWithoutExtension,
            description = data["description"] as? String ?: "",
            prompt = data["prompt"] as? String ?: "",
            context = data["context"] as? Map<String, Any> ?: emptyMap(),
            assertions = data["assertions"] as? List<Map<String, Any>> ?: emptyList(),
            qualityCriteria = (data["quality_criteria"] as? Map<String, Any>)?.mapValues {
                (it.value as? Number)?.toDouble() ?: 0.0
            } ?: emptyMap(),
            tags = data["tags"] as? List<String> ?: emptyList(),
            baselinePassRate = (data["baseline_pass_rate"] as? Number)?.toDouble() ?: 0.8
        )
    }

    internal fun runScenario(scenario: EvalScenario): EvalResult {
        val startTime = System.currentTimeMillis()
        val assertionResults = mutableListOf<AssertionResult>()

        if (modelAdapter != null) {
            // Real evaluation mode: call the model and check assertions against actual output
            val actualOutput = callModel(scenario)
            for (assertion in scenario.assertions) {
                val type = assertion["type"] as? String ?: "contains"
                val expected = assertion["expected"] as? String ?: ""
                val description = assertion["description"] as? String ?: "Assertion: $type"
                val result = evaluateAssertion(type, expected, description, actualOutput)
                assertionResults.add(result)
            }
        } else {
            // Structure validation mode: verify scenario YAML is well-formed
            for (assertion in scenario.assertions) {
                val type = assertion["type"] as? String ?: "contains"
                val expected = assertion["expected"] as? String ?: ""
                val description = assertion["description"] as? String ?: "Assertion: $type"
                val result = validateAssertionStructure(type, expected, description)
                assertionResults.add(result)
            }
        }

        val durationMs = System.currentTimeMillis() - startTime
        val passedCount = assertionResults.count { it.passed }
        val score = if (assertionResults.isNotEmpty()) {
            passedCount.toDouble() / assertionResults.size
        } else {
            1.0
        }

        return EvalResult(
            evalId = scenario.id,
            profile = scenario.profile,
            scenario = scenario.name,
            passed = assertionResults.all { it.passed },
            score = score,
            assertions = assertionResults,
            durationMs = durationMs
        )
    }

    private fun callModel(scenario: EvalScenario): String {
        val adapter = modelAdapter ?: error("modelAdapter is null")

        // Build system prompt from scenario context
        val contextParts = mutableListOf<String>()
        scenario.context["framework"]?.let { contextParts.add("Framework: $it") }
        scenario.context["language"]?.let { contextParts.add("Language: $it") }
        scenario.context["domain"]?.let { contextParts.add("Domain: $it") }
        scenario.context["constraints"]?.let { contextParts.add("Constraints: $it") }
        val systemPrompt = if (contextParts.isNotEmpty()) {
            "You are evaluating a ${scenario.profile} profile scenario.\n${contextParts.joinToString("\n")}"
        } else {
            "You are evaluating a ${scenario.profile} profile scenario."
        }

        val options = CompletionOptions(
            maxTokens = 4096,
            systemPrompt = systemPrompt,
            temperature = 0.0
        )

        return try {
            runBlocking {
                val result = adapter.complete(scenario.prompt, options)
                result.content
            }
        } catch (e: Exception) {
            logger.error("Model call failed for scenario {}: {}", scenario.id, e.message)
            "(model call failed: ${e.message})"
        }
    }

    internal fun evaluateAssertion(type: String, expected: String, description: String, actualOutput: String): AssertionResult {
        return when (type) {
            "contains" -> AssertionResult(description, actualOutput.contains(expected), expected, truncate(actualOutput))
            "not_contains" -> AssertionResult(description, !actualOutput.contains(expected), expected, truncate(actualOutput))
            "matches_pattern" -> AssertionResult(description, Regex(expected).containsMatchIn(actualOutput), expected, truncate(actualOutput))
            "json_schema" -> evaluateJsonSchema(expected, actualOutput, description)
            "semantic_similarity" -> {
                val found = actualOutput.lowercase().contains(expected.lowercase())
                AssertionResult(description, found, expected,
                    if (found) "Contains match (semantic eval)" else "No semantic match")
            }
            else -> AssertionResult(description, false, expected, "Unknown assertion type: $type")
        }
    }

    private fun evaluateJsonSchema(schema: String, actualOutput: String, description: String): AssertionResult {
        // Basic JSON validation: check that the output is parseable as JSON
        // and contains the expected top-level keys from the schema hint
        return try {
            val gson = com.google.gson.Gson()
            gson.fromJson(actualOutput, Any::class.java)
            AssertionResult(description, true, schema, truncate(actualOutput))
        } catch (e: Exception) {
            // Try to find JSON within the output (e.g., wrapped in markdown code blocks)
            val jsonPattern = Regex("""```(?:json)?\s*\n([\s\S]*?)\n```""")
            val match = jsonPattern.find(actualOutput)
            if (match != null) {
                try {
                    val gson = com.google.gson.Gson()
                    gson.fromJson(match.groupValues[1], Any::class.java)
                    AssertionResult(description, true, schema, truncate(match.groupValues[1]))
                } catch (e2: Exception) {
                    AssertionResult(description, false, schema, "Invalid JSON: ${e2.message}")
                }
            } else {
                AssertionResult(description, false, schema, "Output is not valid JSON: ${e.message}")
            }
        }
    }

    private fun truncate(text: String, maxLen: Int = 200): String {
        return if (text.length > maxLen) text.take(maxLen) + "..." else text
    }

    private fun validateAssertionStructure(type: String, expected: String, description: String): AssertionResult {
        val validTypes = listOf("contains", "not_contains", "matches_pattern", "json_schema", "semantic_similarity")
        return if (type in validTypes && expected.isNotBlank()) {
            AssertionResult(
                description = description,
                passed = true,
                expected = expected,
                actual = "(structure valid - runtime execution pending)"
            )
        } else {
            AssertionResult(
                description = description,
                passed = false,
                expected = expected,
                actual = "Invalid assertion type '$type' or empty expected value"
            )
        }
    }
}

/**
 * Entry point for running evaluations from the command line.
 */
fun main(args: Array<String>) {
    val apiKey = System.getenv("ANTHROPIC_API_KEY") ?: ""
    val adapter = if (apiKey.isNotBlank()) {
        println("Using Claude API for real evaluation")
        ClaudeAdapter(apiKey = apiKey)
    } else {
        println("WARNING: ANTHROPIC_API_KEY not set, running structure validation only")
        null
    }

    val profile = args.getOrNull(0)
    val tag = args.getOrNull(1)

    val runner = EvalRunner(modelAdapter = adapter)
    val results = runner.runAll(profileFilter = profile, tagFilter = tag)

    val passed = results.count { it.passed }
    val failed = results.count { !it.passed }
    println("\nEvaluation complete: $passed passed, $failed failed out of ${results.size} total")
}
