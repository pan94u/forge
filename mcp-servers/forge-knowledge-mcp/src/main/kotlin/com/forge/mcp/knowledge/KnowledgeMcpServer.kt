package com.forge.mcp.knowledge

import com.forge.mcp.common.*
import com.forge.mcp.knowledge.tools.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
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
 * Interface for all MCP tools. Each tool provides its definition (name, description,
 * input schema) and an execute method that processes arguments and returns a response.
 */
interface McpTool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse
}

/**
 * Base class for MCP servers providing common routing, authentication, metrics,
 * health checks, and audit logging infrastructure.
 */
abstract class McpServerBase(
    private val serverName: String,
    private val port: Int,
    private val version: String = "0.1.0"
) {
    protected val logger = LoggerFactory.getLogger(this::class.java)
    private val startTimeMs = System.currentTimeMillis()
    private val auditLogger: AuditLogger = Slf4jAuditLogger()
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    abstract fun registerTools(): List<McpTool>

    open fun healthCheckProviders(): List<HealthCheckProvider> = emptyList()

    fun start() {
        val tools = registerTools()
        val toolMap = tools.associateBy { it.definition.name }

        logger.info("Starting {} with {} tools on port {}", serverName, tools.size, port)

        embeddedServer(Netty, port = port) {
            configureServer(toolMap, tools)
        }.start(wait = true)
    }

    private fun Application.configureServer(toolMap: Map<String, McpTool>, tools: List<McpTool>) {
        install(ContentNegotiation) {
            json(json)
        }

        install(CallLogging) {
            level = org.slf4j.event.Level.INFO
        }

        install(StatusPages) {
            exception<McpError> { call, cause ->
                call.respond(
                    HttpStatusCode.BadRequest,
                    McpErrorResponse.from(cause)
                )
            }
            exception<Throwable> { call, cause ->
                logger.error("Unhandled exception", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    McpErrorResponse(code = -32603, message = "Internal server error: ${cause.message}")
                )
            }
        }

        routing {
            healthRoutes(version, startTimeMs, healthCheckProviders())
            metricsRoute()

            // MCP tool listing endpoint
            get("/tools") {
                val toolDefinitions = tools.map { it.definition }
                call.respond(HttpStatusCode.OK, ToolListResponse(toolDefinitions))
            }

            // MCP tool execution endpoint
            post("/tools/{toolName}") {
                val toolName = call.parameters["toolName"]
                    ?: throw McpError.InvalidArguments("Tool name is required")

                val tool = toolMap[toolName]
                    ?: throw McpError.ToolNotFound(toolName)

                val bearerToken = call.request.header(HttpHeaders.Authorization)
                    ?.removePrefix("Bearer ")?.trim()
                val userId = bearerToken?.let { extractUserId(it) } ?: "anonymous"

                val request = call.receive<ToolCallRequest>()
                val startTime = System.currentTimeMillis()
                var success = true

                try {
                    val response = tool.execute(request.arguments, userId)
                    val durationMs = System.currentTimeMillis() - startTime

                    if (response.isError) success = false
                    McpMetrics.recordToolCall(toolName, durationMs, success)

                    auditLogger.log(
                        AuditEntry.create(
                            userId = userId,
                            timestamp = Instant.now(),
                            tool = toolName,
                            params = request.arguments.toMap().mapValues { it.value.toString() },
                            result = if (success) "success" else "error",
                            durationMs = durationMs
                        )
                    )

                    call.respond(HttpStatusCode.OK, response)
                } catch (e: McpError) {
                    success = false
                    val durationMs = System.currentTimeMillis() - startTime
                    McpMetrics.recordToolCall(toolName, durationMs, success)
                    throw e
                } catch (e: Exception) {
                    success = false
                    val durationMs = System.currentTimeMillis() - startTime
                    McpMetrics.recordToolCall(toolName, durationMs, success)
                    throw McpError.InternalError(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Extracts user ID from a Bearer token. In production this would call the
     * AuthProvider; here we do a lightweight extraction for audit purposes.
     */
    private fun extractUserId(token: String): String {
        // In a full implementation, this delegates to OAuthAuthProvider.
        // For now, we decode the JWT subject claim if present.
        return try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                val jsonPayload = json.parseToJsonElement(payload)
                if (jsonPayload is JsonObject) {
                    val sub = jsonPayload["sub"]
                    sub?.toString()?.trim('"') ?: "unknown"
                } else "unknown"
            } else "unknown"
        } catch (_: Exception) {
            "unknown"
        }
    }
}

/**
 * Knowledge MCP Server — provides tools for searching and managing organizational
 * knowledge including wikis, ADRs, runbooks, and API documentation.
 */
class KnowledgeMcpServer : McpServerBase(
    serverName = "forge-knowledge-mcp",
    port = System.getenv("PORT")?.toIntOrNull() ?: 8081
) {
    override fun registerTools(): List<McpTool> {
        val wikiBaseUrl = System.getenv("WIKI_BASE_URL") ?: "http://localhost:8090"
        val wikiApiToken = System.getenv("WIKI_API_TOKEN") ?: ""

        return listOf(
            WikiSearchTool(wikiBaseUrl, wikiApiToken),
            AdrSearchTool(wikiBaseUrl, wikiApiToken),
            RunbookSearchTool(wikiBaseUrl, wikiApiToken),
            ApiDocSearchTool(wikiBaseUrl, wikiApiToken),
            PageCreateTool(wikiBaseUrl, wikiApiToken),
            KnowledgeGapLogTool()
        )
    }
}

fun main() {
    KnowledgeMcpServer().start()
}
