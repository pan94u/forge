package com.forge.mcp.database.security

import com.forge.mcp.common.McpError
import org.slf4j.LoggerFactory

/**
 * Access control for database operations.
 *
 * Enforces:
 * - Whitelist of allowed databases/schemas
 * - Blacklist of production databases (always blocked)
 * - Per-user access level validation
 *
 * Configuration is read from environment variables:
 * - FORGE_DB_ALLOWED_DATABASES: Comma-separated list of allowed database names
 * - FORGE_DB_BLOCKED_DATABASES: Comma-separated list of blocked database names (production)
 * - FORGE_DB_ALLOWED_SCHEMAS: Comma-separated list of allowed schema names
 * - FORGE_DB_BLOCKED_SCHEMAS: Comma-separated list of blocked schema names
 */
class AccessControl {

    private val logger = LoggerFactory.getLogger(AccessControl::class.java)

    /**
     * Access levels for database operations.
     */
    enum class AccessLevel {
        /** Can view schema metadata only */
        SCHEMA_READ,
        /** Can execute SELECT queries */
        DATA_READ,
        /** Full read access including data dictionary */
        FULL_READ
    }

    /**
     * Databases that are always blocked regardless of configuration.
     * These are common production database name patterns.
     */
    private val hardcodedBlockedPatterns = listOf(
        Regex(".*prod.*", RegexOption.IGNORE_CASE),
        Regex(".*production.*", RegexOption.IGNORE_CASE),
        Regex(".*live.*", RegexOption.IGNORE_CASE),
        Regex(".*master.*", RegexOption.IGNORE_CASE)
    )

    private val allowedDatabases: Set<String> = System.getenv("FORGE_DB_ALLOWED_DATABASES")
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()
        ?: emptySet()

    private val blockedDatabases: Set<String> = System.getenv("FORGE_DB_BLOCKED_DATABASES")
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()
        ?: emptySet()

    private val allowedSchemas: Set<String> = System.getenv("FORGE_DB_ALLOWED_SCHEMAS")
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()
        ?: setOf("public", "information_schema")

    private val blockedSchemas: Set<String> = System.getenv("FORGE_DB_BLOCKED_SCHEMAS")
        ?.split(",")?.map { it.trim().lowercase() }?.filter { it.isNotBlank() }?.toSet()
        ?: setOf("pg_catalog", "pg_toast")

    /**
     * Per-user access levels. In production, this would be loaded from a
     * configuration store or derived from OAuth roles.
     * Format: FORGE_DB_USER_ACCESS_{USERID}=LEVEL
     */
    private val defaultAccessLevel = AccessLevel.SCHEMA_READ

    /**
     * Validates that the given user has access to the specified database.
     *
     * @param database the database name to validate
     * @param userId the user requesting access
     * @param requiredLevel the minimum access level needed for the operation
     * @throws McpError.Unauthorized if access is denied
     */
    fun validateAccess(database: String, userId: String, requiredLevel: AccessLevel) {
        val dbLower = database.lowercase()

        // Check hardcoded production block patterns
        for (pattern in hardcodedBlockedPatterns) {
            if (pattern.matches(dbLower)) {
                logger.warn(
                    "Access denied: user '{}' attempted to access production database '{}' (matched pattern: {})",
                    userId, database, pattern.pattern
                )
                throw McpError.Unauthorized(
                    "Access denied: database '$database' appears to be a production database. " +
                        "Production database access is not allowed through this tool."
                )
            }
        }

        // Check explicit blocked list
        if (dbLower in blockedDatabases) {
            logger.warn("Access denied: user '{}' attempted to access blocked database '{}'", userId, database)
            throw McpError.Unauthorized(
                "Access denied: database '$database' is in the blocked list."
            )
        }

        // Check allowed list (if configured, act as whitelist)
        if (allowedDatabases.isNotEmpty() && dbLower !in allowedDatabases) {
            logger.warn(
                "Access denied: user '{}' attempted to access non-whitelisted database '{}'",
                userId, database
            )
            throw McpError.Unauthorized(
                "Access denied: database '$database' is not in the allowed databases list."
            )
        }

        // Check user access level
        val userLevel = getUserAccessLevel(userId)
        if (userLevel.ordinal < requiredLevel.ordinal) {
            logger.warn(
                "Access denied: user '{}' has access level {} but requires {} for database '{}'",
                userId, userLevel, requiredLevel, database
            )
            throw McpError.Unauthorized(
                "Access denied: insufficient access level. You have $userLevel but $requiredLevel is required."
            )
        }

        logger.debug("Access granted: user '{}' -> database '{}' at level {}", userId, database, requiredLevel)
    }

    /**
     * Validates that the given schema is accessible.
     *
     * @param schema the schema name to validate
     * @throws McpError.Unauthorized if the schema is blocked
     */
    fun validateSchema(schema: String) {
        val schemaLower = schema.lowercase()

        if (schemaLower in blockedSchemas) {
            throw McpError.Unauthorized("Access denied: schema '$schema' is blocked.")
        }

        if (allowedSchemas.isNotEmpty() && schemaLower !in allowedSchemas) {
            throw McpError.Unauthorized("Access denied: schema '$schema' is not in the allowed schemas list.")
        }
    }

    /**
     * Returns the access level for the given user.
     */
    fun getUserAccessLevel(userId: String): AccessLevel {
        val envKey = "FORGE_DB_USER_ACCESS_${userId.uppercase().replace("-", "_")}"
        val levelStr = System.getenv(envKey)

        return when (levelStr?.uppercase()) {
            "FULL_READ" -> AccessLevel.FULL_READ
            "DATA_READ" -> AccessLevel.DATA_READ
            "SCHEMA_READ" -> AccessLevel.SCHEMA_READ
            else -> defaultAccessLevel
        }
    }
}
