package com.forge.mcp.database.security

import com.forge.mcp.common.McpError
import org.slf4j.LoggerFactory

/**
 * SQL query sanitizer that validates queries are read-only SELECT statements.
 *
 * Security checks performed:
 * 1. Validates query is a SELECT statement (no INSERT/UPDATE/DELETE/DROP/ALTER/CREATE/TRUNCATE)
 * 2. Checks for common SQL injection patterns
 * 3. Enforces query timeout and row limit via query wrapping
 * 4. Blocks dangerous functions and operations
 */
class QuerySanitizer(
    private val defaultRowLimit: Int = 100,
    private val maxRowLimit: Int = 10_000,
    private val queryTimeoutSeconds: Int = 30
) {
    private val logger = LoggerFactory.getLogger(QuerySanitizer::class.java)

    companion object {
        /**
         * SQL keywords that indicate a write or DDL operation.
         * These are categorically blocked.
         */
        private val BLOCKED_KEYWORDS = setOf(
            "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
            "TRUNCATE", "REPLACE", "MERGE", "UPSERT", "GRANT", "REVOKE",
            "EXEC", "EXECUTE", "CALL", "INTO"
        )

        /**
         * Patterns that may indicate SQL injection attempts.
         */
        private val INJECTION_PATTERNS = listOf(
            Regex("--\\s*$", RegexOption.MULTILINE),           // SQL line comments at end
            Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), // Block comments
            Regex(";\\s*(?:DROP|ALTER|DELETE|INSERT|UPDATE|CREATE|TRUNCATE)", RegexOption.IGNORE_CASE),
            Regex("UNION\\s+ALL\\s+SELECT", RegexOption.IGNORE_CASE),
            Regex("OR\\s+1\\s*=\\s*1", RegexOption.IGNORE_CASE),
            Regex("'\\s*OR\\s+'", RegexOption.IGNORE_CASE),
            Regex("SLEEP\\s*\\(", RegexOption.IGNORE_CASE),
            Regex("BENCHMARK\\s*\\(", RegexOption.IGNORE_CASE),
            Regex("LOAD_FILE\\s*\\(", RegexOption.IGNORE_CASE),
            Regex("pg_sleep\\s*\\(", RegexOption.IGNORE_CASE),
            Regex("WAITFOR\\s+DELAY", RegexOption.IGNORE_CASE)
        )

        /**
         * Dangerous SQL functions that should be blocked.
         */
        private val BLOCKED_FUNCTIONS = setOf(
            "pg_read_file", "pg_ls_dir", "pg_stat_file",
            "lo_import", "lo_export",
            "dblink", "dblink_exec",
            "copy", "pg_dump"
        )
    }

    /**
     * Result of query sanitization containing the validated and potentially modified query.
     */
    data class SanitizedQuery(
        val sql: String,
        val rowLimit: Int,
        val timeoutSeconds: Int
    )

    /**
     * Validates and sanitizes the given SQL query.
     *
     * @param query the raw SQL query to validate
     * @param requestedLimit the requested row limit (will be capped at maxRowLimit)
     * @return a [SanitizedQuery] with the validated SQL and effective limits
     * @throws McpError.InvalidArguments if the query fails validation
     */
    fun sanitize(query: String, requestedLimit: Int = defaultRowLimit): SanitizedQuery {
        val trimmedQuery = query.trim()

        if (trimmedQuery.isBlank()) {
            throw McpError.InvalidArguments("Query must not be empty")
        }

        // Check for multiple statements (semicolons)
        val statementCount = trimmedQuery.count { it == ';' }
        if (statementCount > 1 || (statementCount == 1 && !trimmedQuery.endsWith(";"))) {
            throw McpError.InvalidArguments(
                "Only single SELECT statements are allowed. Multiple statements detected."
            )
        }

        // Remove trailing semicolon for analysis
        val queryForAnalysis = trimmedQuery.trimEnd(';').trim()

        // Verify it starts with SELECT or WITH (CTE)
        val upperQuery = queryForAnalysis.uppercase().trimStart()
        if (!upperQuery.startsWith("SELECT") && !upperQuery.startsWith("WITH")) {
            throw McpError.InvalidArguments(
                "Only SELECT queries are allowed. Query must start with SELECT or WITH."
            )
        }

        // Check for blocked keywords
        checkBlockedKeywords(queryForAnalysis)

        // Check for SQL injection patterns
        checkInjectionPatterns(queryForAnalysis)

        // Check for blocked functions
        checkBlockedFunctions(queryForAnalysis)

        // Enforce row limit
        val effectiveLimit = requestedLimit.coerceIn(1, maxRowLimit)

        // If query doesn't already have a LIMIT, wrap it
        val finalSql = if (!upperQuery.contains("LIMIT")) {
            "$queryForAnalysis LIMIT $effectiveLimit"
        } else {
            // Validate existing LIMIT isn't too high
            val existingLimitMatch = Regex("LIMIT\\s+(\\d+)", RegexOption.IGNORE_CASE).find(queryForAnalysis)
            val existingLimit = existingLimitMatch?.groupValues?.get(1)?.toIntOrNull()
            if (existingLimit != null && existingLimit > maxRowLimit) {
                logger.warn("Query LIMIT {} exceeds maximum {}, capping", existingLimit, maxRowLimit)
                queryForAnalysis.replace(
                    Regex("LIMIT\\s+\\d+", RegexOption.IGNORE_CASE),
                    "LIMIT $maxRowLimit"
                )
            } else {
                queryForAnalysis
            }
        }

        logger.debug("Query sanitized successfully: {} chars, limit={}", finalSql.length, effectiveLimit)

        return SanitizedQuery(
            sql = finalSql,
            rowLimit = effectiveLimit,
            timeoutSeconds = queryTimeoutSeconds
        )
    }

    /**
     * Checks the query for blocked SQL keywords indicating write/DDL operations.
     */
    private fun checkBlockedKeywords(query: String) {
        // Tokenize on word boundaries and check each token
        val tokens = query.uppercase().split(Regex("\\s+|\\(|\\)|,|;"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        for (token in tokens) {
            if (token in BLOCKED_KEYWORDS) {
                logger.warn("Blocked keyword '{}' found in query", token)
                throw McpError.InvalidArguments(
                    "Query contains blocked keyword: $token. Only SELECT queries are allowed."
                )
            }
        }

        // Special case: SELECT ... INTO (which creates tables)
        if (Regex("SELECT\\s+.*\\s+INTO\\s+", RegexOption.IGNORE_CASE).containsMatchIn(query)) {
            throw McpError.InvalidArguments(
                "SELECT INTO is not allowed. Only pure SELECT queries are permitted."
            )
        }
    }

    /**
     * Checks for common SQL injection patterns.
     */
    private fun checkInjectionPatterns(query: String) {
        for (pattern in INJECTION_PATTERNS) {
            if (pattern.containsMatchIn(query)) {
                logger.warn("SQL injection pattern detected in query: {}", pattern.pattern)
                throw McpError.InvalidArguments(
                    "Query contains a potentially unsafe pattern. Please simplify the query."
                )
            }
        }
    }

    /**
     * Checks for blocked database functions.
     */
    private fun checkBlockedFunctions(query: String) {
        val lowerQuery = query.lowercase()
        for (func in BLOCKED_FUNCTIONS) {
            if (lowerQuery.contains(func)) {
                logger.warn("Blocked function '{}' found in query", func)
                throw McpError.InvalidArguments(
                    "Query contains blocked function: $func"
                )
            }
        }
    }
}
