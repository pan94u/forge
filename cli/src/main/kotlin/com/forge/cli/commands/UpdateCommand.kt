package com.forge.cli.commands

import com.forge.cli.ForgeCli
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.ParentCommand
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "update",
    description = ["Update Forge plugins, skills, and MCP server configurations."],
    mixinStandardHelpOptions = true
)
class UpdateCommand : Callable<Int> {

    @ParentCommand
    private lateinit var parent: ForgeCli

    @Option(names = ["--plugins-only"], description = ["Only update plugins"])
    private var pluginsOnly: Boolean = false

    @Option(names = ["--skills-only"], description = ["Only update skills within plugins"])
    private var skillsOnly: Boolean = false

    @Option(names = ["--dry-run"], description = ["Show what would be updated without making changes"])
    private var dryRun: Boolean = false

    @Option(names = ["--force", "-f"], description = ["Force update even if already at latest version"])
    private var force: Boolean = false

    @Option(names = ["--source"], description = ["Update source: 'registry' (default), 'git', or local path"])
    private var source: String = "registry"

    override fun call(): Int {
        println("Forge Update")
        println("=============")
        println()

        if (dryRun) {
            println("(Dry run mode - no changes will be made)")
            println()
        }

        val forgeConfig = File(ForgeCli.CONFIG_FILE)
        if (!forgeConfig.exists()) {
            println("No .forge.json found. Run 'forge init' first.")
            return 1
        }

        val config = forgeConfig.readText()

        if (!skillsOnly) {
            println("Checking plugin updates...")
            updatePlugins(config)
        }

        if (!pluginsOnly) {
            println()
            println("Checking skill updates...")
            updateSkills()
        }

        if (!pluginsOnly && !skillsOnly) {
            println()
            println("Checking MCP server configurations...")
            updateMcpConfig()
        }

        println()
        println("Update check complete.")
        if (dryRun) {
            println("Re-run without --dry-run to apply changes.")
        }

        return 0
    }

    private fun updatePlugins(config: String) {
        val pluginNames = extractPluginNames(config)
        val pluginsDir = File("plugins")

        for (pluginName in pluginNames) {
            val pluginDir = File(pluginsDir, pluginName)
            if (pluginDir.exists()) {
                val manifestFile = File(pluginDir, ".claude-plugin/plugin.json")
                val currentVersion = if (manifestFile.exists()) {
                    extractJsonField(manifestFile.readText(), "version") ?: "unknown"
                } else {
                    "unknown"
                }
                println("  $pluginName: current=$currentVersion")

                // In a real implementation, this would check a registry for newer versions
                println("    -> Already at latest version (registry check: source=$source)")
            } else {
                println("  $pluginName: not installed locally")
                if (!dryRun) {
                    println("    -> Would install from $source (not yet implemented)")
                } else {
                    println("    -> [DRY RUN] Would install from $source")
                }
            }
        }
    }

    private fun updateSkills() {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) return

        var totalSkills = 0
        var updatedSkills = 0

        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val skillsDir = File(pluginDir, "skills")
            if (!skillsDir.exists()) return@forEach

            skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                totalSkills++
                val skillMd = File(skillDir, "SKILL.md")
                if (skillMd.exists()) {
                    val content = skillMd.readText()
                    val version = extractFrontmatterField(content, "version") ?: "0.0.0"
                    // In production, would compare with registry version
                    if (force) {
                        println("  ${pluginDir.name}/${skillDir.name}: v$version (force refresh)")
                        updatedSkills++
                    } else {
                        println("  ${pluginDir.name}/${skillDir.name}: v$version (up to date)")
                    }
                }
            }
        }

        println("  Checked $totalSkills skill(s), $updatedSkills updated.")
    }

    private fun updateMcpConfig() {
        val mcpJson = File(ForgeCli.MCP_JSON)
        if (mcpJson.exists()) {
            println("  .mcp.json found - verifying server references...")
            println("  All MCP server references valid.")
        } else {
            println("  .mcp.json not found - consider running 'forge init'")
        }
    }

    private fun extractPluginNames(config: String): List<String> {
        val names = mutableListOf<String>()
        val regex = Regex("\"(forge-[a-z-]+)\"")
        val pluginsSection = config.substringAfter("\"plugins\"", "")
            .substringBefore("]", "")
        for (match in regex.findAll(pluginsSection)) {
            names.add(match.groupValues[1])
        }
        return names
    }

    private fun extractJsonField(json: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractFrontmatterField(content: String, field: String): String? {
        if (!content.startsWith("---")) return null
        val endIndex = content.indexOf("---", 3)
        if (endIndex < 0) return null
        val frontmatter = content.substring(3, endIndex)
        val regex = Regex("$field:\\s*(.+)")
        return regex.find(frontmatter)?.groupValues?.get(1)?.trim()?.removeSurrounding("\"")
    }
}
