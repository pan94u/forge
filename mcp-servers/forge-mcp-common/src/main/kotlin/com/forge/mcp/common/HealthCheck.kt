package com.forge.mcp.common

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Represents the health status of a service, including sub-component checks.
 */
@Serializable
data class HealthStatus(
    val status: String,
    val version: String,
    val uptime: Long,
    val checks: Map<String, String>
) {
    companion object {
        const val STATUS_UP = "UP"
        const val STATUS_DOWN = "DOWN"
        const val STATUS_DEGRADED = "DEGRADED"

        const val CHECK_OK = "ok"
        const val CHECK_FAIL = "fail"
    }
}

/**
 * Interface for components that contribute health check information.
 * Implementations should perform lightweight checks (e.g., ping a database)
 * and return a map of check-name to status-string.
 */
interface HealthCheckProvider {
    /**
     * Performs health checks and returns results as a map.
     * Keys are check names (e.g., "database", "cache"); values are status strings.
     */
    suspend fun check(): Map<String, String>
}

/**
 * Registers health check routes on the given [Route].
 *
 * Endpoints:
 * - GET /health — full health status with all component checks
 * - GET /health/live — simple liveness probe (always returns 200 if the process is running)
 * - GET /health/ready — readiness probe (returns 200 only if all checks pass)
 *
 * @param version         the service version string to include in the response
 * @param startTimeMillis the epoch millis when the server started, used to compute uptime
 * @param providers       zero or more [HealthCheckProvider] instances whose checks are aggregated
 */
fun Route.healthRoutes(
    version: String,
    startTimeMillis: Long,
    providers: List<HealthCheckProvider> = emptyList()
) {
    route("/health") {
        get {
            val healthStatus = buildHealthStatus(version, startTimeMillis, providers)
            val httpStatus = when (healthStatus.status) {
                HealthStatus.STATUS_UP -> HttpStatusCode.OK
                HealthStatus.STATUS_DEGRADED -> HttpStatusCode.OK
                else -> HttpStatusCode.ServiceUnavailable
            }
            call.respond(httpStatus, healthStatus)
        }

        get("/live") {
            call.respond(HttpStatusCode.OK, mapOf("status" to "alive"))
        }

        get("/ready") {
            val healthStatus = buildHealthStatus(version, startTimeMillis, providers)
            if (healthStatus.status == HealthStatus.STATUS_DOWN) {
                call.respond(HttpStatusCode.ServiceUnavailable, healthStatus)
            } else {
                call.respond(HttpStatusCode.OK, healthStatus)
            }
        }
    }
}

/**
 * Aggregates checks from all providers and determines the overall status.
 */
private suspend fun buildHealthStatus(
    version: String,
    startTimeMillis: Long,
    providers: List<HealthCheckProvider>
): HealthStatus {
    val allChecks = mutableMapOf<String, String>()

    for (provider in providers) {
        try {
            allChecks.putAll(provider.check())
        } catch (e: Exception) {
            allChecks["${provider::class.simpleName ?: "unknown"}_error"] = "fail: ${e.message}"
        }
    }

    val overallStatus = when {
        allChecks.isEmpty() -> HealthStatus.STATUS_UP
        allChecks.values.all { it == HealthStatus.CHECK_OK } -> HealthStatus.STATUS_UP
        allChecks.values.any { it.startsWith("fail") } -> HealthStatus.STATUS_DEGRADED
        else -> HealthStatus.STATUS_UP
    }

    val uptimeMs = System.currentTimeMillis() - startTimeMillis

    return HealthStatus(
        status = overallStatus,
        version = version,
        uptime = uptimeMs,
        checks = allChecks
    )
}
