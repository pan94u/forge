package com.forge.mcp.common

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * Abstract base class for all Forge MCP servers.
 *
 * Provides a fully configured Ktor/Netty server with:
 * - JSON content negotiation (kotlinx.serialization)
 * - OAuth2 Bearer token authentication
 * - Health check endpoints (/health, /health/live, /health/ready)
 * - Metrics endpoint (/metrics)
 * - MCP protocol endpoints (POST /mcp/tools/list, POST /mcp/tools/call)
 * - Structured audit trail logging
 *
 * Subclasses implement [serverName], [serverVersion], [listTools], and [callTool]
 * to define the actual tool surface.
 *
 * @param authProvider       validates incoming Bearer tokens
 * @param auditLogger        records audit trail entries
 * @param healthCheckProviders optional health check contributors
 * @param port               the port to listen on (default 8080)
 * @param host               the host interface to bind to (default 0.0.0.0)
 */
abstract class McpServerBase(
    private val authProvider: AuthProvider,
    private val auditLogger: AuditLogger = Slf4jAuditLogger(),
    private val healthCheckProviders: List<HealthCheckProvider> = emptyList(),
    private val port: Int = 8080,
    private val host: String = "0.0.0.0"
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val startTimeMs = System.currentTimeMillis()

    private val jsonConfig = Json {
        prettyPrint = false
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /** The human-readable name of this MCP server. */
    abstract val serverName: String

    /** The version string for this server (e.g., "1.0.0"). */
    abstract val serverVersion: String

    /**
     * Returns the list of tools this server exposes.
     * Called on POST /mcp/tools/list.
     */
    abstract suspend fun listTools(): List<ToolDefinition>

    /**
     * Executes the named tool with the given arguments.
     * Called on POST /mcp/tools/call.
     *
     * @param name      the tool name as declared in [listTools]
     * @param arguments the JSON arguments supplied by the caller
     * @return the tool call response
     * @throws McpError.ToolNotFound if the tool name is unknown
     * @throws McpError.InvalidArguments if the arguments are malformed
     */
    abstract suspend fun callTool(name: String, arguments: JsonObject): ToolCallResponse

    /**
     * Hook for subclasses to register additional Ktor routes (e.g., custom
     * endpoints beyond the MCP protocol).
     */
    protected open fun Routing.additionalRoutes() {}

    /**
     * Starts the embedded Netty server. This call blocks until the server is stopped.
     */
    fun start() {
        logger.info("Starting {} v{} on {}:{}", serverName, serverVersion, host, port)
        embeddedServer(Netty, port = port, host = host) {
            configureServer()
        }.start(wait = true)
    }

    /**
     * Configures all Ktor plugins and routing for the application.
     */
    private fun Application.configureServer() {
        installPlugins()
        configureRouting()
    }

    private fun Application.installPlugins() {
        install(ContentNegotiation) {
            json(jsonConfig)
        }

        install(CallLogging) {
            level = org.slf4j.event.Level.INFO
            filter { call -> call.request.path().startsWith("/mcp") }
            format { call ->
                val status = call.response.status()
                val method = call.request.httpMethod.value
                val path = call.request.path()
                "$method $path -> $status"
            }
        }

        install(StatusPages) {
            exception<McpError> { call, cause ->
                logger.warn("MCP error: code={} message={}", cause.code, cause.message)
                val httpStatus = when (cause) {
                    is McpError.ToolNotFound -> HttpStatusCode.NotFound
                    is McpError.InvalidArguments -> HttpStatusCode.BadRequest
                    is McpError.Unauthorized -> HttpStatusCode.Unauthorized
                    is McpError.InternalError -> HttpStatusCode.InternalServerError
                }
                call.respond(httpStatus, McpErrorResponse.from(cause))
            }

            exception<Throwable> { call, cause ->
                logger.error("Unhandled exception", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    McpErrorResponse(code = -32603, message = "Internal server error")
                )
            }
        }

        install(Authentication) {
            bearer("mcp-auth") {
                authenticate { tokenCredential ->
                    val result = authProvider.validateToken(tokenCredential.token)
                    if (result.valid) {
                        McpPrincipal(
                            userId = result.userId,
                            roles = result.roles,
                            token = tokenCredential.token
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }

    private fun Application.configureRouting() {
        routing {
            // Public endpoints (no auth required)
            healthRoutes(
                version = serverVersion,
                startTimeMillis = startTimeMs,
                providers = healthCheckProviders
            )
            metricsRoute()

            // Authenticated MCP endpoints
            authenticate("mcp-auth") {
                route("/mcp") {
                    post("/tools/list") {
                        val principal = call.principal<McpPrincipal>()
                            ?: throw McpError.Unauthorized()

                        logger.debug("tools/list requested by user={}", principal.userId)

                        val tools = listTools()
                        call.respond(HttpStatusCode.OK, ToolListResponse(tools = tools))
                    }

                    post("/tools/call") {
                        val principal = call.principal<McpPrincipal>()
                            ?: throw McpError.Unauthorized()

                        val request = call.receive<ToolCallRequest>()
                        logger.info(
                            "Tool call: tool={} user={}", request.name, principal.userId
                        )

                        val startTime = System.currentTimeMillis()
                        var success = true
                        var resultSummary = "ok"

                        try {
                            val response = callTool(request.name, request.arguments)
                            val durationMs = System.currentTimeMillis() - startTime

                            if (response.isError) {
                                success = false
                                resultSummary = "error"
                            }

                            McpMetrics.recordToolCall(request.name, durationMs, success)

                            auditLogger.log(
                                AuditEntry(
                                    userId = principal.userId,
                                    timestamp = Instant.now(),
                                    tool = request.name,
                                    params = request.arguments,
                                    result = resultSummary,
                                    durationMs = durationMs
                                )
                            )

                            call.respond(HttpStatusCode.OK, response)
                        } catch (e: McpError) {
                            val durationMs = System.currentTimeMillis() - startTime
                            McpMetrics.recordToolCall(request.name, durationMs, false)

                            auditLogger.log(
                                AuditEntry(
                                    userId = principal.userId,
                                    timestamp = Instant.now(),
                                    tool = request.name,
                                    params = request.arguments,
                                    result = "error: ${e.message}",
                                    durationMs = durationMs
                                )
                            )

                            throw e
                        } catch (e: Exception) {
                            val durationMs = System.currentTimeMillis() - startTime
                            McpMetrics.recordToolCall(request.name, durationMs, false)

                            auditLogger.log(
                                AuditEntry(
                                    userId = principal.userId,
                                    timestamp = Instant.now(),
                                    tool = request.name,
                                    params = request.arguments,
                                    result = "error: ${e.message}",
                                    durationMs = durationMs
                                )
                            )

                            throw McpError.InternalError(
                                e.message ?: "Unknown error during tool execution"
                            )
                        }
                    }
                }
            }

            // Allow subclasses to add routes
            additionalRoutes()
        }
    }
}

/**
 * Ktor authentication principal representing an authenticated MCP user.
 */
data class McpPrincipal(
    val userId: String,
    val roles: List<String>,
    val token: String
) : Principal
