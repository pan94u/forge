package com.forge.webide.service

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/**
 * Records Forge-specific metrics using Micrometer.
 *
 * Exposes counters and timers via /actuator/metrics and /actuator/prometheus.
 */
@Service
class MetricsService(private val meterRegistry: MeterRegistry) {

    // --- Counters ---

    fun recordProfileRoute(profileName: String, method: String) {
        meterRegistry.counter(
            "forge.profile.route",
            listOf(Tag.of("profile", profileName), Tag.of("method", method))
        ).increment()
    }

    fun recordToolCall(toolName: String, success: Boolean) {
        val status = if (success) "success" else "error"
        meterRegistry.counter(
            "forge.tool.calls",
            listOf(Tag.of("tool", toolName), Tag.of("status", status))
        ).increment()
    }

    fun recordBaselineResult(baselineName: String, passed: Boolean) {
        val result = if (passed) "pass" else "fail"
        meterRegistry.counter(
            "forge.baseline.results",
            listOf(Tag.of("baseline", baselineName), Tag.of("result", result))
        ).increment()
    }

    fun recordOodaPhase(phase: String) {
        meterRegistry.counter(
            "forge.ooda.phases",
            listOf(Tag.of("phase", phase))
        ).increment()
    }

    fun recordSkillLoaded(profileName: String, skillCount: Int) {
        meterRegistry.gauge(
            "forge.skill.loaded",
            listOf(Tag.of("profile", profileName)),
            skillCount.toDouble()
        )
    }

    // --- Timers ---

    fun recordMessageDuration(durationMs: Long) {
        meterRegistry.timer("forge.message.duration").record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordTurnDuration(turnNumber: Int, durationMs: Long) {
        meterRegistry.timer(
            "forge.turn.duration",
            listOf(Tag.of("turn", turnNumber.toString()))
        ).record(durationMs, TimeUnit.MILLISECONDS)
    }

    fun recordToolDuration(toolName: String, durationMs: Long) {
        meterRegistry.timer(
            "forge.tool.duration",
            listOf(Tag.of("tool", toolName))
        ).record(durationMs, TimeUnit.MILLISECONDS)
    }
}
