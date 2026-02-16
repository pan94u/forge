package com.forge.mcp.database.tools

import com.forge.mcp.common.*
import com.forge.mcp.database.DatabaseConnectionManager
import com.forge.mcp.database.McpTool
import com.forge.mcp.database.security.AccessControl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Searches the data dictionary for column descriptions, business meanings,
 * and data lineage information.
 *
 * Input:
 * - query (string, required): Search query for data dictionary entries
 * - database (string, optional): Filter by specific database
 *
 * Returns column descriptions, business meanings, and data lineage.
 */
class DataDictionaryTool(
    private val connectionManager: DatabaseConnectionManager,
    private val accessControl: AccessControl
) : McpTool {

    private val logger = LoggerFactory.getLogger(DataDictionaryTool::class.java)

    override val definition = ToolDefinition(
        name = "data_dictionary",
        description = "Search the data dictionary for column descriptions, business meanings, and data lineage. Helps understand what data columns represent in business terms.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("query") {
                    put("type", "string")
                    put("description", "Search query for data dictionary entries (column name, table name, or business term)")
                }
                putJsonObject("database") {
                    put("type", "string")
                    put("description", "Filter by specific database name (optional)")
                }
            }
            putJsonArray("required") {
                add("query")
            }
        }
    )

    @Serializable
    data class DataDictionaryEntry(
        val database: String,
        val schema: String,
        val table: String,
        val column: String,
        val dataType: String,
        val description: String?,
        val businessMeaning: String?,
        val exampleValues: List<String>,
        val nullablePercentage: Double?,
        val distinctCountEstimate: Long?
    )

    @Serializable
    data class DataDictionaryResponse(
        val entries: List<DataDictionaryEntry>,
        val totalCount: Int,
        val query: String
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val query = arguments["query"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'query' is required")

        val database = arguments["database"]?.jsonPrimitive?.contentOrNull

        return try {
            val databases = if (database != null) {
                accessControl.validateAccess(database, userId, AccessControl.AccessLevel.FULL_READ)
                listOf(database)
            } else {
                connectionManager.configuredDatabases().filter { dbName ->
                    try {
                        accessControl.validateAccess(dbName, userId, AccessControl.AccessLevel.FULL_READ)
                        true
                    } catch (_: McpError) {
                        false
                    }
                }
            }

            val allEntries = mutableListOf<DataDictionaryEntry>()

            for (dbName in databases) {
                try {
                    val entries = searchDataDictionary(dbName, query)
                    allEntries.addAll(entries)
                } catch (e: Exception) {
                    logger.warn("Failed to search data dictionary in database '{}': {}", dbName, e.message)
                }
            }

            val response = DataDictionaryResponse(
                entries = allEntries.take(50),
                totalCount = allEntries.size,
                query = query
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Data dictionary search failed: {}", e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Data dictionary search failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Searches the PostgreSQL system catalog for columns matching the query.
     * Uses column names, table names, and column comments for matching.
     */
    private fun searchDataDictionary(dbName: String, query: String): List<DataDictionaryEntry> {
        val dataSource = connectionManager.getDataSource(dbName)
        val entries = mutableListOf<DataDictionaryEntry>()

        dataSource.connection.use { conn ->
            // Search columns using PostgreSQL information_schema and comments
            val sql = """
                SELECT
                    c.table_schema,
                    c.table_name,
                    c.column_name,
                    c.data_type,
                    c.is_nullable,
                    c.column_default,
                    col_description(
                        (quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass,
                        c.ordinal_position
                    ) as column_comment,
                    (
                        SELECT n_distinct
                        FROM pg_stats
                        WHERE schemaname = c.table_schema
                        AND tablename = c.table_name
                        AND attname = c.column_name
                    ) as n_distinct,
                    (
                        SELECT null_frac
                        FROM pg_stats
                        WHERE schemaname = c.table_schema
                        AND tablename = c.table_name
                        AND attname = c.column_name
                    ) as null_frac
                FROM information_schema.columns c
                WHERE c.table_schema NOT IN ('pg_catalog', 'pg_toast', 'information_schema')
                AND (
                    c.column_name ILIKE '%' || ? || '%'
                    OR c.table_name ILIKE '%' || ? || '%'
                    OR col_description(
                        (quote_ident(c.table_schema) || '.' || quote_ident(c.table_name))::regclass,
                        c.ordinal_position
                    ) ILIKE '%' || ? || '%'
                )
                ORDER BY c.table_schema, c.table_name, c.ordinal_position
                LIMIT 100
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, query)
                stmt.setString(2, query)
                stmt.setString(3, query)
                stmt.queryTimeout = 15

                val rs = stmt.executeQuery()
                while (rs.next()) {
                    val tableSchema = rs.getString("table_schema")
                    val tableName = rs.getString("table_name")
                    val columnName = rs.getString("column_name")
                    val dataType = rs.getString("data_type")
                    val columnComment = rs.getString("column_comment")
                    val nDistinct = rs.getDouble("n_distinct")
                    val nullFrac = rs.getDouble("null_frac")

                    entries.add(
                        DataDictionaryEntry(
                            database = dbName,
                            schema = tableSchema,
                            table = tableName,
                            column = columnName,
                            dataType = dataType,
                            description = columnComment,
                            businessMeaning = columnComment, // In production, mapped from a separate business glossary
                            exampleValues = emptyList(), // Would be populated from pg_stats.most_common_vals
                            nullablePercentage = if (!rs.wasNull()) (nullFrac * 100) else null,
                            distinctCountEstimate = if (!rs.wasNull() && nDistinct > 0) nDistinct.toLong() else null
                        )
                    )
                }
                rs.close()
            }
        }

        return entries
    }
}
