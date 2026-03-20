package com.forge.eval.engine.grader

import com.forge.eval.protocol.EvalGrade
import com.forge.eval.protocol.EvalTranscript
import com.forge.eval.protocol.GraderConfig
import java.util.UUID

/**
 * Common interface for all graders (Code-based, Model-based, Human).
 * Phase 1 implements only CodeBasedGrader; others will follow in later phases.
 */
interface Grader {
    /**
     * Grade a trial's output.
     *
     * @param trialId The trial being graded
     * @param output The agent's text output
     * @param config The grader configuration
     * @param transcript Optional transcript for tool-call/trajectory assertions
     * @return The grade
     */
    suspend fun grade(
        trialId: UUID,
        output: String,
        config: GraderConfig,
        transcript: EvalTranscript? = null
    ): EvalGrade
}
