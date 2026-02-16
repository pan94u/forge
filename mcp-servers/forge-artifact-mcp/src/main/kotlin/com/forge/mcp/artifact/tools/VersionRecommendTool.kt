package com.forge.mcp.artifact.tools

import com.forge.mcp.common.*
import com.forge.mcp.artifact.McpTool
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory

/**
 * Recommends artifact version upgrades based on vulnerability data and
 * version compatibility analysis.
 *
 * Input:
 * - groupId (string, required): Maven group ID
 * - artifactId (string, required): Maven artifact ID
 * - currentVersion (string, required): Currently used version
 *
 * Returns recommended version, breaking changes summary, and migration guide link.
 */
class VersionRecommendTool(
    private val nexusUrl: String,
    private val nexusToken: String,
    private val osvApiUrl: String
) : McpTool {

    private val logger = LoggerFactory.getLogger(VersionRecommendTool::class.java)

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
        engine {
            requestTimeout = 30_000
        }
    }

    override val definition = ToolDefinition(
        name = "version_recommend",
        description = "Get version upgrade recommendations for a Maven artifact. Analyzes security vulnerabilities, compatibility, and provides migration guidance.",
        inputSchema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("groupId") {
                    put("type", "string")
                    put("description", "Maven group ID (e.g., 'org.springframework.boot')")
                }
                putJsonObject("artifactId") {
                    put("type", "string")
                    put("description", "Maven artifact ID (e.g., 'spring-boot-starter-web')")
                }
                putJsonObject("currentVersion") {
                    put("type", "string")
                    put("description", "Currently used version (e.g., '2.7.14')")
                }
            }
            putJsonArray("required") {
                add("groupId")
                add("artifactId")
                add("currentVersion")
            }
        }
    )

    @Serializable
    data class VersionRecommendation(
        val recommendedVersion: String,
        val upgradeType: String, // "patch", "minor", "major"
        val reason: String,
        val breakingChanges: List<String>,
        val migrationGuideUrl: String?,
        val vulnerabilitiesFixed: Int,
        val isSecurityUpdate: Boolean
    )

    @Serializable
    data class VersionRecommendResponse(
        val groupId: String,
        val artifactId: String,
        val currentVersion: String,
        val recommendations: List<VersionRecommendation>,
        val availableVersions: List<String>,
        val endOfLife: Boolean,
        val summary: String
    )

    override suspend fun execute(arguments: JsonObject, userId: String): ToolCallResponse {
        val groupId = arguments["groupId"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'groupId' is required")

        val artifactId = arguments["artifactId"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'artifactId' is required")

        val currentVersion = arguments["currentVersion"]?.jsonPrimitive?.contentOrNull
            ?: throw McpError.InvalidArguments("'currentVersion' is required")

        return try {
            // Fetch available versions
            val availableVersions = fetchAvailableVersions(groupId, artifactId)
            val stableVersions = availableVersions.filter { isStableVersion(it) }

            // Parse current version
            val current = parseVersion(currentVersion)

            // Find recommended versions at different upgrade levels
            val recommendations = mutableListOf<VersionRecommendation>()

            // Latest patch version (same major.minor)
            val latestPatch = stableVersions.filter { v ->
                val parsed = parseVersion(v)
                parsed.major == current.major && parsed.minor == current.minor
            }.maxByOrNull { parseVersion(it).patch }

            if (latestPatch != null && latestPatch != currentVersion) {
                val vulnsFixed = countVulnerabilitiesFixed(groupId, artifactId, currentVersion, latestPatch)
                recommendations.add(
                    VersionRecommendation(
                        recommendedVersion = latestPatch,
                        upgradeType = "patch",
                        reason = "Latest patch release with bug fixes" +
                            if (vulnsFixed > 0) " and $vulnsFixed security fixes" else "",
                        breakingChanges = emptyList(),
                        migrationGuideUrl = null,
                        vulnerabilitiesFixed = vulnsFixed,
                        isSecurityUpdate = vulnsFixed > 0
                    )
                )
            }

            // Latest minor version (same major)
            val latestMinor = stableVersions.filter { v ->
                val parsed = parseVersion(v)
                parsed.major == current.major && parsed.minor > current.minor
            }.maxByOrNull { v ->
                val parsed = parseVersion(v)
                parsed.minor * 1000 + parsed.patch
            }

            if (latestMinor != null) {
                val vulnsFixed = countVulnerabilitiesFixed(groupId, artifactId, currentVersion, latestMinor)
                recommendations.add(
                    VersionRecommendation(
                        recommendedVersion = latestMinor,
                        upgradeType = "minor",
                        reason = "Latest minor release with new features and improvements",
                        breakingChanges = listOf("Check release notes for potential behavioral changes"),
                        migrationGuideUrl = generateMigrationUrl(groupId, artifactId, currentVersion, latestMinor),
                        vulnerabilitiesFixed = vulnsFixed,
                        isSecurityUpdate = vulnsFixed > 0
                    )
                )
            }

            // Latest major version
            val latestMajor = stableVersions.filter { v ->
                parseVersion(v).major > current.major
            }.maxByOrNull { v ->
                val parsed = parseVersion(v)
                parsed.major * 1_000_000 + parsed.minor * 1000 + parsed.patch
            }

            if (latestMajor != null) {
                val vulnsFixed = countVulnerabilitiesFixed(groupId, artifactId, currentVersion, latestMajor)
                recommendations.add(
                    VersionRecommendation(
                        recommendedVersion = latestMajor,
                        upgradeType = "major",
                        reason = "Latest major release. May contain breaking API changes.",
                        breakingChanges = listOf(
                            "Major version upgrade may include breaking API changes",
                            "Review migration guide before upgrading",
                            "Consider running in a feature branch first"
                        ),
                        migrationGuideUrl = generateMigrationUrl(groupId, artifactId, currentVersion, latestMajor),
                        vulnerabilitiesFixed = vulnsFixed,
                        isSecurityUpdate = vulnsFixed > 0
                    )
                )
            }

            // Check if current version line is end-of-life
            val currentMajor = current.major
            val hasNewerMajor = stableVersions.any { parseVersion(it).major > currentMajor }
            val noRecentPatch = latestPatch == null || latestPatch == currentVersion

            val summary = when {
                recommendations.isEmpty() -> "You are on the latest available version."
                recommendations.any { it.isSecurityUpdate && it.upgradeType == "patch" } ->
                    "Security patches available. Upgrade to ${recommendations.first { it.isSecurityUpdate }.recommendedVersion} recommended."
                recommendations.any { it.upgradeType == "patch" } ->
                    "Patch update available: ${recommendations.first { it.upgradeType == "patch" }.recommendedVersion}"
                else -> "${recommendations.size} version upgrade(s) available."
            }

            val response = VersionRecommendResponse(
                groupId = groupId,
                artifactId = artifactId,
                currentVersion = currentVersion,
                recommendations = recommendations,
                availableVersions = stableVersions.takeLast(15).reversed(),
                endOfLife = hasNewerMajor && noRecentPatch,
                summary = summary
            )

            ToolCallResponse(
                content = listOf(
                    ToolContent.Json(Json.encodeToJsonElement(response))
                )
            )
        } catch (e: McpError) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "Version recommendation failed for {}:{}:{}: {}",
                groupId, artifactId, currentVersion, e.message, e
            )
            ToolCallResponse(
                content = listOf(ToolContent.Text("Version recommendation failed: ${e.message}")),
                isError = true
            )
        }
    }

    /**
     * Fetches available versions from Maven Central.
     */
    private suspend fun fetchAvailableVersions(groupId: String, artifactId: String): List<String> {
        return try {
            val response = httpClient.get("https://search.maven.org/solrsearch/select") {
                parameter("q", "g:\"$groupId\" AND a:\"$artifactId\"")
                parameter("rows", 100)
                parameter("wt", "json")
                parameter("core", "gav")
            }

            if (response.status != HttpStatusCode.OK) return emptyList()

            val body = response.body<JsonObject>()
            val docs = body["response"]?.jsonObject?.get("docs")?.jsonArray ?: return emptyList()

            docs.mapNotNull { doc ->
                doc.jsonObject["v"]?.jsonPrimitive?.contentOrNull
            }.sorted()
        } catch (e: Exception) {
            logger.warn("Failed to fetch versions for {}:{}: {}", groupId, artifactId, e.message)
            emptyList()
        }
    }

    /**
     * Counts vulnerabilities that exist in the current version but are fixed
     * in the target version by querying the OSV database.
     */
    private suspend fun countVulnerabilitiesFixed(
        groupId: String,
        artifactId: String,
        currentVersion: String,
        targetVersion: String
    ): Int {
        return try {
            // Query vulnerabilities for the current version
            val requestBody = buildJsonObject {
                putJsonObject("package") {
                    put("name", "$groupId:$artifactId")
                    put("ecosystem", "Maven")
                }
                put("version", currentVersion)
            }

            val response = httpClient.post("$osvApiUrl/v1/query") {
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }

            if (response.status != HttpStatusCode.OK) return 0

            val body = response.body<JsonObject>()
            val vulns = body["vulns"]?.jsonArray ?: return 0

            // Count vulnerabilities that have a fix at or before the target version
            vulns.count { vuln ->
                val affected = vuln.jsonObject["affected"]?.jsonArray ?: return@count false
                affected.any { aff ->
                    val ranges = aff.jsonObject["ranges"]?.jsonArray ?: return@any false
                    ranges.any { range ->
                        val events = range.jsonObject["events"]?.jsonArray ?: return@any false
                        events.any { event ->
                            val fixedVersion = event.jsonObject["fixed"]?.jsonPrimitive?.contentOrNull
                            fixedVersion != null && compareVersions(fixedVersion, targetVersion) <= 0
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Vulnerability count check failed: {}", e.message)
            0
        }
    }

    data class SemanticVersion(val major: Int, val minor: Int, val patch: Int, val raw: String)

    private fun parseVersion(version: String): SemanticVersion {
        val parts = version.split(".", "-").take(3)
        return SemanticVersion(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0,
            raw = version
        )
    }

    private fun isStableVersion(version: String): Boolean {
        val lower = version.lowercase()
        return !lower.contains("snapshot") &&
            !lower.contains("alpha") &&
            !lower.contains("beta") &&
            !lower.contains("-rc") &&
            !lower.contains("-m")
    }

    private fun compareVersions(v1: String, v2: String): Int {
        val p1 = parseVersion(v1)
        val p2 = parseVersion(v2)
        val majorDiff = p1.major.compareTo(p2.major)
        if (majorDiff != 0) return majorDiff
        val minorDiff = p1.minor.compareTo(p2.minor)
        if (minorDiff != 0) return minorDiff
        return p1.patch.compareTo(p2.patch)
    }

    /**
     * Generates a likely migration guide URL based on common patterns.
     */
    private fun generateMigrationUrl(
        groupId: String,
        artifactId: String,
        fromVersion: String,
        toVersion: String
    ): String? {
        // Well-known migration guide patterns
        return when {
            groupId.startsWith("org.springframework") ->
                "https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-${parseVersion(toVersion).major}.${parseVersion(toVersion).minor}-Migration-Guide"
            groupId.startsWith("io.ktor") ->
                "https://ktor.io/docs/migrating-${parseVersion(fromVersion).major}.html"
            else -> null
        }
    }
}
