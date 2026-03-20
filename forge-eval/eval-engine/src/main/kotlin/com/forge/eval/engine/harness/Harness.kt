package com.forge.eval.engine.harness

import com.forge.eval.engine.TrialOutput
import com.forge.eval.protocol.EvalTask

/**
 * Harness interface — isolates task execution from grading.
 *
 * Different harness types provide different execution environments:
 * - PassthroughHarness: For external transcripts (no execution, just grade)
 * - MockLlmHarness: Deterministic replay (for regression tests)
 * - DockerHarness: Isolated Docker container execution (for coding agents)
 */
interface Harness {
    /** Execute a task and return the trial output */
    suspend fun execute(task: EvalTask): TrialOutput

    /** Harness type identifier */
    val type: HarnessType
}

enum class HarnessType {
    PASSTHROUGH,
    MOCK_LLM,
    DOCKER
}

/**
 * Passthrough harness — used when external agents submit transcripts.
 * No execution happens; the output is provided externally.
 */
class PassthroughHarness(
    private val output: String = "",
    private val outputProvider: ((EvalTask) -> TrialOutput)? = null
) : Harness {
    override val type = HarnessType.PASSTHROUGH

    override suspend fun execute(task: EvalTask): TrialOutput {
        return outputProvider?.invoke(task) ?: TrialOutput(output = output)
    }
}

/**
 * Mock LLM harness — replays predetermined responses for deterministic testing.
 * Maps task IDs to fixed responses for reproducible eval runs.
 */
class MockLlmHarness(
    private val responses: Map<String, String> = emptyMap(),
    private val defaultResponse: String = "(mock response)"
) : Harness {
    override val type = HarnessType.MOCK_LLM

    override suspend fun execute(task: EvalTask): TrialOutput {
        val response = responses[task.id.toString()]
            ?: responses[task.name]
            ?: defaultResponse
        return TrialOutput(output = response)
    }
}

/**
 * Docker harness — executes tasks in isolated Docker containers.
 *
 * For coding agent evaluation:
 * 1. Spins up a Docker container with the workspace
 * 2. Injects the task prompt
 * 3. Captures output (files, stdout, exit code)
 * 4. Tears down the container
 *
 * Note: This is the interface definition. Actual Docker integration
 * requires docker-java or testcontainers dependency.
 */
class DockerHarness(
    private val imageName: String = "forge-eval-sandbox:latest",
    private val timeoutSeconds: Long = 300,
    private val memoryLimitMb: Long = 512
) : Harness {
    override val type = HarnessType.DOCKER

    data class DockerConfig(
        val imageName: String,
        val timeoutSeconds: Long,
        val memoryLimitMb: Long,
        val environmentVariables: Map<String, String> = emptyMap(),
        val volumeMounts: Map<String, String> = emptyMap()
    )

    override suspend fun execute(task: EvalTask): TrialOutput {
        // Phase 5: Docker execution stub
        // Real implementation would use docker-java or testcontainers
        return TrialOutput(
            output = "(docker execution not yet implemented — task: ${task.name})",
            error = "Docker harness not yet implemented"
        )
    }
}
