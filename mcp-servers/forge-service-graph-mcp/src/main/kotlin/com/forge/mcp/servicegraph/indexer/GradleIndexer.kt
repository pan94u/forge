package com.forge.mcp.servicegraph.indexer

import com.forge.mcp.servicegraph.ServiceGraphStore
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Indexes service dependencies from Gradle build files.
 *
 * Scans Gradle build files (build.gradle.kts / build.gradle) to discover
 * inter-service dependencies declared as project dependencies.
 *
 * Example: `implementation(project(":services:payment-service"))` indicates a
 * dependency on payment-service.
 */
class GradleIndexer(
    private val rootDir: String = System.getenv("FORGE_REPO_ROOT") ?: "."
) {
    private val logger = LoggerFactory.getLogger(GradleIndexer::class.java)

    companion object {
        /**
         * Regex for Kotlin DSL project dependencies.
         */
        private val KOTLIN_PROJECT_DEP = Regex(
            """(?:implementation|api|runtimeOnly|compileOnly)\s*\(\s*project\s*\(\s*"([^"]+)"\s*\)"""
        )

        /**
         * Regex for Groovy DSL project dependencies.
         */
        private val GROOVY_PROJECT_DEP = Regex(
            """(?:implementation|api|runtimeOnly|compileOnly)\s+project\s*\(\s*['"]([^'"]+)['"]\s*\)"""
        )

        /**
         * Regex for settings.gradle.kts include statements to discover modules.
         */
        private val INCLUDE_PATTERN = Regex("""include\s*\(\s*"([^"]+)"\s*\)""")
    }

    /**
     * Indexes all Gradle build files under the root directory and populates
     * the ServiceGraphStore with discovered dependencies.
     */
    fun index() {
        logger.info("Starting Gradle dependency indexing from root: {}", rootDir)

        val rootFile = File(rootDir)
        if (!rootFile.isDirectory) {
            logger.warn("Root directory does not exist: {}", rootDir)
            return
        }

        // Discover modules from settings.gradle.kts
        val modules = discoverModules(rootFile)
        logger.info("Discovered {} Gradle modules", modules.size)

        // Process each module's build file
        for (module in modules) {
            val modulePath = module.replace(":", "/").removePrefix("/")
            val moduleName = module.split(":").last()

            val buildFileKts = File(rootFile, "$modulePath/build.gradle.kts")
            val buildFileGroovy = File(rootFile, "$modulePath/build.gradle")

            val buildFile = when {
                buildFileKts.exists() -> buildFileKts
                buildFileGroovy.exists() -> buildFileGroovy
                else -> continue
            }

            try {
                val content = buildFile.readText()
                val dependencies = extractProjectDependencies(content)

                for (dep in dependencies) {
                    val depName = dep.split(":").last()
                    ServiceGraphStore.addEdge(
                        ServiceGraphStore.ServiceEdge(
                            from = moduleName,
                            to = depName,
                            protocol = "in-process",
                            description = "Gradle project dependency",
                            isSynchronous = true
                        )
                    )
                }

                if (dependencies.isNotEmpty()) {
                    logger.debug("Module '{}' depends on: {}", moduleName, dependencies)
                }
            } catch (e: Exception) {
                logger.warn("Failed to parse build file for module '{}': {}", module, e.message)
            }
        }

        logger.info("Gradle dependency indexing complete")
    }

    /**
     * Discovers all modules listed in settings.gradle.kts or settings.gradle.
     */
    private fun discoverModules(rootFile: File): List<String> {
        val settingsKts = File(rootFile, "settings.gradle.kts")
        val settingsGroovy = File(rootFile, "settings.gradle")

        val settingsFile = when {
            settingsKts.exists() -> settingsKts
            settingsGroovy.exists() -> settingsGroovy
            else -> return emptyList()
        }

        val content = settingsFile.readText()
        return INCLUDE_PATTERN.findAll(content).map { it.groupValues[1] }.toList()
    }

    /**
     * Extracts project dependency references from a build file's content.
     */
    private fun extractProjectDependencies(content: String): List<String> {
        val ktsMatches = KOTLIN_PROJECT_DEP.findAll(content).map { it.groupValues[1] }.toList()
        val groovyMatches = GROOVY_PROJECT_DEP.findAll(content).map { it.groupValues[1] }.toList()
        return (ktsMatches + groovyMatches).distinct()
    }
}
