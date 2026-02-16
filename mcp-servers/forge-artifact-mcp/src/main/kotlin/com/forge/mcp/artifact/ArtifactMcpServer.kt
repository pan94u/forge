package com.forge.mcp.artifact

import com.forge.mcp.common.*
import com.forge.mcp.artifact.tools.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
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
 * Interface for all MCP tools within the artifact server.
 */
interface McpTool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse
}

/**
 * Artifact MCP Server — provides tools for searching artifacts, scanning for
 * vulnerabilities, and getting version recommendations.
 */
class ArtifactMcpServer {

    private val logger = LoggerFactory.getLogger(ArtifactMcpServer::class.java)
    private val startTimeMs = System.currentTimeMillis()
    private val auditLogger: AuditLogger = Slf4jAuditLogger()
    private val version = "0.1.0"
    private val port = System.getenv("PORT")?.toIntOrNull() ?: 8084

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun registerTools(): List<McpTool> {
        val nexusUrl = System.getenv("FORGE_NEXUS_URL") ?: "https://repo.maven.apache.org/maven2"
        val nexusToken = System.getenv("FORGE_NEXUS_TOKEN") ?: ""
        val osvApiUrl = System.getenv("FORGE_OSV_API_URL") ?: "https://api.osv.dev"

        return listOf(
            DependencySearchTool(nexusUrl, nexusToken),
            VulnerabilityScanTool(osvApiUrl),
            VersionRecommendTool(nexusUrl, nexusToken, osvApiUrl)
        )
    }

    fun start() {
        val tools = registerTools()
        val toolMap = tools.associateBy { it.definition.name }

        logger.info("Starting forge-artifact-mcp with {} tools on port {}", tools.size, port)

        embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json(json)
            }

            install(CallLogging) {
                level = org.slf4j.event.Level.INFO
            }

            install(StatusPages) {
                exception<McpError> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, McpErrorResponse.from(cause))
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
                healthRoutes(version, startTimeMs)
                metricsRoute()

                get("/tools") {
                    val toolDefinitions = tools.map { it.definition }
                    call.respond(HttpStatusCode.OK, ToolListResponse(toolDefinitions))
                }

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
        }.start(wait = true)
    }

    private fun extractUserId(token: String): String {
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

fun main() {
    ArtifactMcpServer().start()
}
