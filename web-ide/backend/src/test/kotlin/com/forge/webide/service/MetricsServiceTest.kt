package com.forge.webide.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MetricsServiceTest {

    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metricsService: MetricsService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        metricsService = MetricsService(meterRegistry)
    }

    // --- Counter tests ---

    @Test
    fun `recordProfileRoute increments counter with correct tags`() {
        metricsService.recordProfileRoute("development-profile", "keyword")
        metricsService.recordProfileRoute("development-profile", "keyword")
        metricsService.recordProfileRoute("testing-profile", "semantic")

        val devCounter = meterRegistry.find("forge.profile.route")
            .tag("profile", "development-profile")
            .tag("method", "keyword")
            .counter()

        assertThat(devCounter).isNotNull
        assertThat(devCounter!!.count()).isEqualTo(2.0)

        val testCounter = meterRegistry.find("forge.profile.route")
            .tag("profile", "testing-profile")
            .tag("method", "semantic")
            .counter()

        assertThat(testCounter).isNotNull
        assertThat(testCounter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `recordToolCall increments counter with success status`() {
        metricsService.recordToolCall("search_knowledge", true)
        metricsService.recordToolCall("search_knowledge", true)
        metricsService.recordToolCall("search_knowledge", false)

        val successCounter = meterRegistry.find("forge.tool.calls")
            .tag("tool", "search_knowledge")
            .tag("status", "success")
            .counter()

        assertThat(successCounter).isNotNull
        assertThat(successCounter!!.count()).isEqualTo(2.0)

        val errorCounter = meterRegistry.find("forge.tool.calls")
            .tag("tool", "search_knowledge")
            .tag("status", "error")
            .counter()

        assertThat(errorCounter).isNotNull
        assertThat(errorCounter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `recordBaselineResult increments counter with pass-fail tags`() {
        metricsService.recordBaselineResult("code-quality", true)
        metricsService.recordBaselineResult("code-quality", false)

        val passCounter = meterRegistry.find("forge.baseline.results")
            .tag("baseline", "code-quality")
            .tag("result", "pass")
            .counter()

        assertThat(passCounter).isNotNull
        assertThat(passCounter!!.count()).isEqualTo(1.0)

        val failCounter = meterRegistry.find("forge.baseline.results")
            .tag("baseline", "code-quality")
            .tag("result", "fail")
            .counter()

        assertThat(failCounter).isNotNull
        assertThat(failCounter!!.count()).isEqualTo(1.0)
    }

    @Test
    fun `recordOodaPhase increments counter with phase tag`() {
        metricsService.recordOodaPhase("observe")
        metricsService.recordOodaPhase("orient")
        metricsService.recordOodaPhase("decide")
        metricsService.recordOodaPhase("act")
        metricsService.recordOodaPhase("complete")

        val observeCounter = meterRegistry.find("forge.ooda.phases")
            .tag("phase", "observe")
            .counter()

        assertThat(observeCounter).isNotNull
        assertThat(observeCounter!!.count()).isEqualTo(1.0)

        val actCounter = meterRegistry.find("forge.ooda.phases")
            .tag("phase", "act")
            .counter()

        assertThat(actCounter).isNotNull
        assertThat(actCounter!!.count()).isEqualTo(1.0)
    }

    // --- Timer tests ---

    @Test
    fun `recordMessageDuration records timer`() {
        metricsService.recordMessageDuration(1500)
        metricsService.recordMessageDuration(2500)

        val timer = meterRegistry.find("forge.message.duration").timer()

        assertThat(timer).isNotNull
        assertThat(timer!!.count()).isEqualTo(2)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(4000.0)
    }

    @Test
    fun `recordTurnDuration records timer with turn tag`() {
        metricsService.recordTurnDuration(1, 800)
        metricsService.recordTurnDuration(2, 1200)

        val turn1Timer = meterRegistry.find("forge.turn.duration")
            .tag("turn", "1")
            .timer()

        assertThat(turn1Timer).isNotNull
        assertThat(turn1Timer!!.count()).isEqualTo(1)
        assertThat(turn1Timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(800.0)

        val turn2Timer = meterRegistry.find("forge.turn.duration")
            .tag("turn", "2")
            .timer()

        assertThat(turn2Timer).isNotNull
        assertThat(turn2Timer!!.count()).isEqualTo(1)
    }

    @Test
    fun `recordToolDuration records timer with tool tag`() {
        metricsService.recordToolDuration("search_knowledge", 350)
        metricsService.recordToolDuration("search_knowledge", 450)
        metricsService.recordToolDuration("run_baseline", 1000)

        val searchTimer = meterRegistry.find("forge.tool.duration")
            .tag("tool", "search_knowledge")
            .timer()

        assertThat(searchTimer).isNotNull
        assertThat(searchTimer!!.count()).isEqualTo(2)
        assertThat(searchTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(800.0)

        val baselineTimer = meterRegistry.find("forge.tool.duration")
            .tag("tool", "run_baseline")
            .timer()

        assertThat(baselineTimer).isNotNull
        assertThat(baselineTimer!!.count()).isEqualTo(1)
    }
}
