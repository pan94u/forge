package com.forge.eval.protocol

/** Platform that owns the evaluation suite */
enum class Platform {
    FORGE,
    SYNAPSE,
    APPLICATION
}

/** Type of agent being evaluated */
enum class AgentType {
    CODING,
    CONVERSATIONAL,
    RESEARCH,
    COMPUTER_USE
}

/** Lifecycle stage of an evaluation task */
enum class Lifecycle {
    CAPABILITY,
    REGRESSION,
    SATURATED
}

/** Outcome of a single eval trial */
enum class TrialOutcome {
    PASS,
    FAIL,
    PARTIAL,
    ERROR
}

/** Type of grader used */
enum class GraderType {
    CODE_BASED,
    MODEL_BASED,
    HUMAN
}

/** Source of an evaluation transcript */
enum class TranscriptSource {
    FORGE,
    SYNAPSE,
    EXTERNAL
}

/** Difficulty level for eval tasks */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD,
    EXPERT
}

/** Status of an eval run */
enum class RunStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}
