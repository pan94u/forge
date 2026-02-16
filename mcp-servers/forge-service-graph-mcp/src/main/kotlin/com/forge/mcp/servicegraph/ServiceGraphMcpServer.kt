package com.forge.mcp.servicegraph

import com.forge.mcp.common.*
import com.forge.mcp.servicegraph.tools.*
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for all MCP tools within the service graph server.
 */
interface McpTool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse
}

/**
 * In-memory service graph data model. Populated by indexers from various sources
 * (Gradle dependencies, OpenAPI specs, K8s manifests, APM traces).
 */
object ServiceGraphStore {

    @Serializable
    data class ServiceNode(
        val name: String,
        val description: String,
        val team: String,
        val techLead: String,
        val onCallContact: String,
        val techStack: List<String>,
        val tags: List<String>,
        val repositoryUrl: String?,
        val dashboardUrl: String?
    )

    @Serializable
    data class ServiceEdge(
        val from: String,
        val to: String,
        val protocol: String, // HTTP, gRPC, Kafka, SQS, etc.
        val description: String?,
        val isSynchronous: Boolean
    )

    private val services = ConcurrentHashMap<String, ServiceNode>()
    private val edges = ConcurrentHashMap<String, MutableList<ServiceEdge>>()

    fun addService(service: ServiceNode) {
        services[service.name] = service
    }

    fun addEdge(edge: ServiceEdge) {
        edges.computeIfAbsent(edge.from) { mutableListOf() }.add(edge)
    }

    fun getService(name: String): ServiceNode? = services[name]

    fun getAllServices(): List<ServiceNode> = services.values.toList()

    fun getUpstreamDependencies(serviceName: String): List<ServiceEdge> {
        return edges.values.flatten().filter { it.to == serviceName }
    }

    fun getDownstreamDependencies(serviceName: String): List<ServiceEdge> {
        return edges[serviceName] ?: emptyList()
    }

    fun findServicesByTeam(team: String): List<ServiceNode> {
        return services.values.filter { it.team.equals(team, ignoreCase = true) }
    }

    fun findServicesByTag(tag: String): List<ServiceNode> {
        return services.values.filter { tag in it.tags }
    }

    /**
     * Finds all paths from one service to another using BFS.
     */
    fun findAllPaths(from: String, to: String, maxDepth: Int = 10): List<List<ServiceEdge>> {
        val result = mutableListOf<List<ServiceEdge>>()
        val queue = ArrayDeque<Pair<String, List<ServiceEdge>>>()
        queue.add(from to emptyList())

        while (queue.isNotEmpty()) {
            val (current, path) = queue.removeFirst()

            if (path.size > maxDepth) continue

            if (current == to && path.isNotEmpty()) {
                result.add(path)
                continue
            }

            val downstream = edges[current] ?: continue
            for (edge in downstream) {
                // Avoid cycles
                if (path.any { it.from == edge.to }) continue
                queue.add(edge.to to (path + edge))
            }
        }

        return result
    }

    /**
     * Performs transitive impact analysis: returns all services that could be
     * affected by a change in the given service.
     */
    fun getTransitiveDownstream(serviceName: String, maxDepth: Int = 5): Set<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<String, Int>>()
        queue.add(serviceName to 0)

        while (queue.isNotEmpty()) {
            val (current, depth) = queue.removeFirst()
            if (depth > maxDepth || current in visited) continue
            visited.add(current)

            val upstream = edges.values.flatten().filter { it.to == current }
            for (edge in upstream) {
                queue.add(edge.from to (depth + 1))
            }
        }

        visited.remove(serviceName) // Don't include the source service
        return visited
    }

    fun clear() {
        services.clear()
        edges.clear()
    }
}

/**
 * Service Graph MCP Server — provides tools for querying the service dependency graph,
 * analyzing change impact, and finding service ownership information.
 */
class ServiceGraphMcpServer {

    private val logger = LoggerFactory.getLogger(ServiceGraphMcpServer::class.java)
    private val startTimeMs = System.currentTimeMillis()
    private val auditLogger: AuditLogger = Slf4jAuditLogger()
    private val version = "0.1.0"
    private val port = System.getenv("PORT")?.toIntOrNull() ?: 8083

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private fun registerTools(): List<McpTool> {
        return listOf(
            ServiceListTool(),
            DependenciesTool(),
            ImpactAnalysisTool(),
            CallChainTool(),
            OwnerTool()
        )
    }

    fun start() {
        val tools = registerTools()
        val toolMap = tools.associateBy { it.definition.name }

        logger.info("Starting forge-service-graph-mcp with {} tools on port {}", tools.size, port)

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
    ServiceGraphMcpServer().start()
}
