package com.forge.mcp.database

import com.forge.mcp.common.*
import com.forge.mcp.database.security.AccessControl
import com.forge.mcp.database.security.QuerySanitizer
import com.forge.mcp.database.tools.*
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
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
import java.util.concurrent.ConcurrentHashMap

/**
 * Interface for all MCP tools within the database server.
 */
interface McpTool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse
}

/**
 * Manages a pool of HikariCP data sources, one per configured database.
 * Connection details are read from environment variables following the pattern:
 * FORGE_DB_{NAME}_URL, FORGE_DB_{NAME}_USER, FORGE_DB_{NAME}_PASSWORD
 */
class DatabaseConnectionManager {
    private val logger = LoggerFactory.getLogger(DatabaseConnectionManager::class.java)
    private val dataSources = ConcurrentHashMap<String, HikariDataSource>()

    /**
     * Returns a HikariDataSource for the given database name.
     * Creates the connection pool on first access using environment variables.
     */
    fun getDataSource(database: String): HikariDataSource {
        return dataSources.computeIfAbsent(database) { dbName ->
            val upperName = dbName.uppercase().replace("-", "_")
            val url = System.getenv("FORGE_DB_${upperName}_URL")
                ?: throw McpError.InvalidArguments("Database '$dbName' is not configured. Set FORGE_DB_${upperName}_URL")
            val user = System.getenv("FORGE_DB_${upperName}_USER") ?: "readonly"
            val password = System.getenv("FORGE_DB_${upperName}_PASSWORD") ?: ""

            logger.info("Creating connection pool for database '{}'", dbName)

            val config = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = 5
                minimumIdle = 1
                connectionTimeout = 10_000
                idleTimeout = 300_000
                maxLifetime = 600_000
                isReadOnly = true
                addDataSourceProperty("ApplicationName", "forge-database-mcp")
            }
            HikariDataSource(config)
        }
    }

    /**
     * Returns the set of configured database names.
     */
    fun configuredDatabases(): Set<String> {
        val envPrefix = "FORGE_DB_"
        val envSuffix = "_URL"
        return System.getenv().keys
            .filter { it.startsWith(envPrefix) && it.endsWith(envSuffix) }
            .map {
                it.removePrefix(envPrefix).removeSuffix(envSuffix).lowercase().replace("_", "-")
            }
            .toSet()
    }

    fun close() {
        dataSources.values.forEach { it.close() }
        dataSources.clear()
    }
}

/**
 * Health check provider that verifies database connectivity.
 */
class DatabaseHealthCheck(
    private val connectionManager: DatabaseConnectionManager
) : HealthCheckProvider {
    override suspend fun check(): Map<String, String> {
        val results = mutableMapOf<String, String>()
        for (dbName in connectionManager.configuredDatabases()) {
            results["db-$dbName"] = try {
                connectionManager.getDataSource(dbName).connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("SELECT 1")
                    }
                }
                HealthStatus.CHECK_OK
            } catch (e: Exception) {
                "fail: ${e.message}"
            }
        }
        return results
    }
}

/**
 * Database MCP Server — provides tools for inspecting schemas, executing
 * read-only queries, and searching data dictionaries with strict security controls.
 */
class DatabaseMcpServer {

    private val logger = LoggerFactory.getLogger(DatabaseMcpServer::class.java)
    private val startTimeMs = System.currentTimeMillis()
    private val auditLogger: AuditLogger = Slf4jAuditLogger()
    private val version = "0.1.0"
    private val port = System.getenv("PORT")?.toIntOrNull() ?: 8082

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    private val connectionManager = DatabaseConnectionManager()
    private val querySanitizer = QuerySanitizer()
    private val accessControl = AccessControl()
    private val healthCheck = DatabaseHealthCheck(connectionManager)

    private fun registerTools(): List<McpTool> {
        return listOf(
            SchemaInspectorTool(connectionManager, accessControl),
            QueryExecutorTool(connectionManager, querySanitizer, accessControl),
            DataDictionaryTool(connectionManager, accessControl)
        )
    }

    fun start() {
        val tools = registerTools()
        val toolMap = tools.associateBy { it.definition.name }

        logger.info("Starting forge-database-mcp with {} tools on port {}", tools.size, port)

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
                healthRoutes(version, startTimeMs, listOf(healthCheck))
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
    DatabaseMcpServer().start()
}
