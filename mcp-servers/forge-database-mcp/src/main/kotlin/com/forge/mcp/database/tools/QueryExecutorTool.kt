package com.forge.mcp.database.tools

import com.forge.mcp.common.*
import com.forge.mcp.database.DatabaseConnectionManager
import com.forge.mcp.database.McpTool
import com.forge.mcp.database.security.AccessControl
import com.forge.mcp.database.security.QuerySanitizer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Executes read-only SELECT queries against configured databases.
 *
 * CRITICAL: All queries are validated through QuerySanitizer to ensure they are
 * SELECT-only. No INSERT, UPDATE, DELETE, DROP, ALTER, CREATE, or TRUNCATE allowed.
 *
 * Input:
 * - database (string, required): Database name
 * - query (string, required): SELECT query to execute
 * - limit (int, optional, default 100): Maximum rows to return
 *
 * Returns result set as JSON with column names and typed values.
 */
class QueryExecutorTool(
    private val connectionManager: DatabaseConnectionManager,
    private val querySanitizer: QuerySanitizer,
    private val accessControl: AccessControl
) : McpTool {

    private val logger = LoggerFactory.getLogger(QueryExecutorTool::class.java)

    override val definition = ToolDefinition(
        name = "query_executor",
        description = "Execute read-only SELECT queries against databases. Queries are validated to be SELECT-only. Returns results as JSON.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("database") {
                    put("type", "string")
                    put("description", "Database name to query")
                }
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "SELECT query to execute. Only SELECT statements are allowed.")
                }
                putJsonObject("limit") {
                    put("type", "integer")
                    put("description", "Maximum number of rows to return (default: 100, max: 10000)")
                    put("default", 100)
                }
            }
            putJsonArray("required") {
                add("database")
                add("query")
            }
        }
    )

    @Serializable
    data class QueryResult(
        val columns: List<ColumnMeta>,
        val rows: List<JsonObject>,
        val rowCount: Int,
        val truncated: Boolean,
        val executionTimeMs: Long,
        val query: String
    )

    @Serializable
    data class ColumnMeta(
        val name: String,
        val type: String
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val database = arguments["database"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'database' is required")

        val rawQuery = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val limit = arguments["limit"]?.jsonPrimitive?.intOrNull ?: 100

        // Validate access
        accessControl.validateAccess(database, userId, AccessControl.AccessLevel.DATA_READ)

        // Sanitize the query — this will throw McpError.InvalidArguments if invalid
        val sanitized = querySanitizer.sanitize(rawQuery, limit)

        return try {
            val dataSource = connectionManager.getDataSource(database)
            val startTime = System.currentTimeMillis()

            val result = dataSource.connection.use { conn ->
                conn.createStatement().use { stmt ->
                    // Set query timeout
                    stmt.queryTimeout = sanitized.timeoutSeconds

                    val rs = stmt.executeQuery(sanitized.sql)
                    val metaData = rs.metaData
                    val columnCount = metaData.columnCount

                    // Extract column metadata
                    val columns = (1..columnCount).map { i ->
                        ColumnMeta(
                            name = metaData.getColumnLabel(i),
                            type = metaData.getColumnTypeName(i)
                        )
                    }

                    // Extract rows
                    val rows = mutableListOf<JsonObject>()
                    var rowsFetched = 0
                    val maxRows = sanitized.rowLimit

                    while (rs.next() && rowsFetched < maxRows) {
                        val row = buildJsonObject {
                            for (i in 1..columnCount) {
                                val colName = metaData.getColumnLabel(i)
                                val value = rs.getObject(i)
                                put(colName, valueToJsonElement(value))
                            }
                        }
                        rows.add(row)
                        rowsFetched++
                    }

                    val truncated = rs.next() // check if there are more rows
                    rs.close()

                    QueryResult(
                        columns = columns,
                        rows = rows,
                        rowCount = rows.size,
                        truncated = truncated,
                        executionTimeMs = System.currentTimeMillis() - startTime,
                        query = sanitized.sql
                    )
                }
            }

            logger.info(
                "Query executed: database={}, rows={}, time={}ms, user={}",
                database, result.rowCount, result.executionTimeMs, userId
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(result))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: java.sql.SQLTimeoutException) {
            logger.warn("Query timed out for database '{}': {}", database, sanitized.sql)
            ToolCallResponse(
                content = listOf(
                    ToolContent.Text("Query timed out after ${sanitized.timeoutSeconds} seconds. Please simplify the query or add more specific filters.")
                ),
                isError = true
            )
        } catch (e: Exception) {
            logger.error("Query execution failed for database '{}': {}", database, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Query execution failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Converts a JDBC value to a kotlinx.serialization JsonElement.
     */
    private fun valueToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Float -> JsonPrimitive(value)
            is Double -> JsonPrimitive(value)
            is java.math.BigDecimal -> JsonPrimitive(value.toDouble())
            is java.math.BigInteger -> JsonPrimitive(value.toLong())
            is Short -> JsonPrimitive(value.toInt())
            is Byte -> JsonPrimitive(value.toInt())
            is java.sql.Timestamp -> JsonPrimitive(value.toInstant().toString())
            is java.sql.Date -> JsonPrimitive(value.toString())
            is java.sql.Time -> JsonPrimitive(value.toString())
            is java.util.UUID -> JsonPrimitive(value.toString())
            is ByteArray -> JsonPrimitive("<binary ${value.size} bytes>")
            is java.sql.Array -> {
                val arr = value.array
                if (arr is Array<*>) {
                    JsonArray(arr.map { valueToJsonElement(it) })
                } else {
                    JsonPrimitive(value.toString())
                }
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}
