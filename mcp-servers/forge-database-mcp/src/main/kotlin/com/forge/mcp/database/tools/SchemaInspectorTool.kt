package com.forge.mcp.database.tools

import com.forge.mcp.common.*
import com.forge.mcp.database.DatabaseConnectionManager
import com.forge.mcp.database.McpTool
import com.forge.mcp.database.security.AccessControl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Inspects database schema metadata including tables, columns, types,
 * constraints, and indexes.
 *
 * Input:
 * - database (string, required): Database name
 * - schema (string, optional): Schema name (default: "public")
 * - table (string, optional): Specific table to inspect
 *
 * Returns table definitions with columns, types, constraints, and indexes.
 */
class SchemaInspectorTool(
    private val connectionManager: DatabaseConnectionManager,
    private val accessControl: AccessControl
) : McpTool {

    private val logger = LoggerFactory.getLogger(SchemaInspectorTool::class.java)

    override val definition = ToolDefinition(
        name = "schema_inspector",
        description = "Inspect database schema: tables, columns, data types, constraints, and indexes. Useful for understanding database structure before writing queries.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("database") {
                    put("type", "string")
                    put("description", "Database name to inspect")
                }
                putJsonObject("schema") {
                    put("type", "string")
                    put("description", "Schema name (default: 'public')")
                    put("default", "public")
                }
                putJsonObject("table") {
                    put("type", "string")
                    put("description", "Specific table name to inspect (optional, omit for all tables)")
                }
            }
            putJsonArray("required") {
                add("database")
            }
        }
    )

    @Serializable
    data class ColumnInfo(
        val name: String,
        val type: String,
        val nullable: Boolean,
        val defaultValue: String?,
        val isPrimaryKey: Boolean,
        val comment: String?
    )

    @Serializable
    data class IndexInfo(
        val name: String,
        val columns: List<String>,
        val isUnique: Boolean,
        val type: String
    )

    @Serializable
    data class ForeignKeyInfo(
        val name: String,
        val columns: List<String>,
        val referencedTable: String,
        val referencedColumns: List<String>
    )

    @Serializable
    data class TableInfo(
        val name: String,
        val schema: String,
        val columns: List<ColumnInfo>,
        val indexes: List<IndexInfo>,
        val foreignKeys: List<ForeignKeyInfo>,
        val rowCountEstimate: Long,
        val comment: String?
    )

    @Serializable
    data class SchemaInspectionResponse(
        val database: String,
        val schema: String,
        val tables: List<TableInfo>
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val database = arguments["database"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'database' is required")

        val schema = arguments["schema"]?.jsonPrimitive?.contentOrNull ?: "public"
        val table = arguments["table"]?.jsonPrimitive?.contentOrNull

        // Validate access
        accessControl.validateAccess(database, userId, AccessControl.AccessLevel.SCHEMA_READ)
        accessControl.validateSchema(schema)

        return try {
            val dataSource = connectionManager.getDataSource(database)
            val tables = mutableListOf<TableInfo>()

            dataSource.connection.use { conn ->
                val metadata = conn.metaData

                // Get tables
                val tableRs = metadata.getTables(null, schema, table ?: "%", arrayOf("TABLE"))
                val tableNames = mutableListOf<String>()
                while (tableRs.next()) {
                    tableNames.add(tableRs.getString("TABLE_NAME"))
                }
                tableRs.close()

                for (tableName in tableNames) {
                    // Get columns
                    val columns = mutableListOf<ColumnInfo>()
                    val primaryKeyColumns = mutableSetOf<String>()

                    // Get primary keys
                    val pkRs = metadata.getPrimaryKeys(null, schema, tableName)
                    while (pkRs.next()) {
                        primaryKeyColumns.add(pkRs.getString("COLUMN_NAME"))
                    }
                    pkRs.close()

                    // Get column details
                    val colRs = metadata.getColumns(null, schema, tableName, "%")
                    while (colRs.next()) {
                        val colName = colRs.getString("COLUMN_NAME")
                        columns.add(
                            ColumnInfo(
                                name = colName,
                                type = colRs.getString("TYPE_NAME") +
                                    colRs.getInt("COLUMN_SIZE").let { size ->
                                        if (size > 0) "($size)" else ""
                                    },
                                nullable = colRs.getInt("NULLABLE") == 1,
                                defaultValue = colRs.getString("COLUMN_DEF"),
                                isPrimaryKey = colName in primaryKeyColumns,
                                comment = colRs.getString("REMARKS")?.takeIf { it.isNotBlank() }
                            )
                        )
                    }
                    colRs.close()

                    // Get indexes
                    val indexes = mutableListOf<IndexInfo>()
                    val indexMap = mutableMapOf<String, MutableList<String>>()
                    val indexUniqueness = mutableMapOf<String, Boolean>()

                    val idxRs = metadata.getIndexInfo(null, schema, tableName, false, false)
                    while (idxRs.next()) {
                        val indexName = idxRs.getString("INDEX_NAME") ?: continue
                        val columnName = idxRs.getString("COLUMN_NAME") ?: continue
                        indexMap.getOrPut(indexName) { mutableListOf() }.add(columnName)
                        indexUniqueness[indexName] = !idxRs.getBoolean("NON_UNIQUE")
                    }
                    idxRs.close()

                    for ((indexName, indexColumns) in indexMap) {
                        indexes.add(
                            IndexInfo(
                                name = indexName,
                                columns = indexColumns,
                                isUnique = indexUniqueness[indexName] ?: false,
                                type = "btree"
                            )
                        )
                    }

                    // Get foreign keys
                    val foreignKeys = mutableListOf<ForeignKeyInfo>()
                    val fkMap = mutableMapOf<String, Triple<MutableList<String>, String, MutableList<String>>>()

                    val fkRs = metadata.getImportedKeys(null, schema, tableName)
                    while (fkRs.next()) {
                        val fkName = fkRs.getString("FK_NAME") ?: "unnamed_fk"
                        val fkColumn = fkRs.getString("FKCOLUMN_NAME")
                        val pkTable = fkRs.getString("PKTABLE_NAME")
                        val pkColumn = fkRs.getString("PKCOLUMN_NAME")

                        val entry = fkMap.getOrPut(fkName) {
                            Triple(mutableListOf(), pkTable, mutableListOf())
                        }
                        entry.first.add(fkColumn)
                        entry.third.add(pkColumn)
                    }
                    fkRs.close()

                    for ((fkName, fkDetails) in fkMap) {
                        foreignKeys.add(
                            ForeignKeyInfo(
                                name = fkName,
                                columns = fkDetails.first,
                                referencedTable = fkDetails.second,
                                referencedColumns = fkDetails.third
                            )
                        )
                    }

                    // Get estimated row count (PostgreSQL-specific)
                    val rowCount = try {
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery(
                                "SELECT reltuples::bigint FROM pg_class WHERE relname = '$tableName'"
                            )
                            if (rs.next()) rs.getLong(1) else -1L
                        }
                    } catch (_: Exception) { -1L }

                    // Get table comment (PostgreSQL-specific)
                    val tableComment = try {
                        conn.createStatement().use { stmt ->
                            val rs = stmt.executeQuery(
                                "SELECT obj_description('$schema.$tableName'::regclass, 'pg_class')"
                            )
                            if (rs.next()) rs.getString(1) else null
                        }
                    } catch (_: Exception) { null }

                    tables.add(
                        TableInfo(
                            name = tableName,
                            schema = schema,
                            columns = columns,
                            indexes = indexes,
                            foreignKeys = foreignKeys,
                            rowCountEstimate = rowCount,
                            comment = tableComment
                        )
                    )
                }
            }

            val response = SchemaInspectionResponse(
                database = database,
                schema = schema,
                tables = tables
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error("Schema inspection failed for database '{}': {}", database, e.message, e)
            ToolCallResponse(
                content = listOf(ToolContent.Text("Schema inspection failed: ${e.message}")),
                isError = true
            )
        }
    }
}
