package com.forge.eval

import com.forge.eval.engine.EvalEngine
import com.forge.eval.engine.EvalRunResult
import com.forge.eval.engine.ReportGenerator
import com.forge.eval.engine.TrialOutput
import com.forge.eval.engine.legacy.YamlEvalSetImporter
import com.forge.eval.protocol.*
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Bridge between the legacy agent-eval module and the new Forge Eval engine.
 *
 * Provides backward compatibility: existing code that uses EvalRunner can migrate
 * incrementally by switching to ForgeEvalBridge, which delegates to the new
 * eval-engine for grading while preserving the same YAML-based workflow.
 */
class ForgeEvalBridge(
    private val evalSetsDir: File = File("agent-eval/eval-sets"),
    private val evalEngine: EvalEngine = EvalEngine(),
    private val reportGenerator: ReportGenerator = ReportGenerator()
) {

    private val logger = LoggerFactory.getLogger(ForgeEvalBridge::class.java)
    private val importer = YamlEvalSetImporter(evalSetsDir)

    /**
     * Import legacy YAML eval-sets and run them through the new engine.
     *
     * @param profileFilter Only run evals for this profile (null = all)
     * @param outputProvider Function that produces output for a given task prompt
     * @return List of legacy-compatible EvalResults
     */
    fun runAll(
        profileFilter: String? = null,
        outputProvider: ((EvalTask) -> TrialOutput)? = null
    ): List<EvalRunner.EvalResult> {
        val importResult = importer.importAll()
        logger.info("Imported {} suites with {} total tasks", importResult.suites.size, importResult.totalTasks)

        val results = mutableListOf<EvalRunner.EvalResult>()

        for (suite in importResult.suites) {
            val profile = suite.tags.firstOrNull() ?: "unknown"
            if (profileFilter != null && profile != profileFilter) continue

            val tasks = importResult.tasksBySuite[suite.id] ?: continue
            if (tasks.isEmpty()) continue

            val provider = outputProvider ?: { task ->
                TrialOutput(output = "(structure validation — no model)")
            }

            val runResult = evalEngine.executeRun(
                suite = suite,
                tasks = tasks,
                trialsPerTask = 1,
                outputProvider = provider
            )

            // Convert back to legacy format
            for (trialResult in runResult.trials) {
                val task = tasks.find { it.id == trialResult.trial.taskId }
                val legacyAssertions = trialResult.grades.flatMap { grade ->
                    grade.assertionResults.map { ar ->
                        EvalRunner.AssertionResult(
                            description = ar.description,
                            passed = ar.passed,
                            expected = ar.expected,
                            actual = ar.actual
                        )
                    }
                }

                results.add(EvalRunner.EvalResult(
                    evalId = task?.name ?: "unknown",
                    profile = profile,
                    scenario = task?.name ?: "unknown",
                    passed = trialResult.trial.outcome == TrialOutcome.PASS,
                    score = trialResult.trial.score,
                    assertions = legacyAssertions,
                    durationMs = trialResult.trial.durationMs
                ))
            }
        }

        return results
    }

    /**
     * Import legacy YAML eval-sets into the new protocol format.
     * Useful for migrating to the REST API.
     */
    fun importEvalSets(): YamlEvalSetImporter.ImportResult {
        return importer.importAll()
    }
}
