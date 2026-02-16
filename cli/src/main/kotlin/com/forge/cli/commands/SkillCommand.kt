package com.forge.cli.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import picocli.CommandLine.Option
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "skill",
    description = ["Manage Forge skills: list, show details, or validate."],
    mixinStandardHelpOptions = true,
    subcommands = [
        SkillListCommand::class,
        SkillShowCommand::class,
        SkillValidateCommand::class
    ]
)
class SkillCommand : Runnable {
    override fun run() {
        println("Use 'forge skill list|show|validate'. Run 'forge skill --help' for details.")
    }
}

@Command(
    name = "list",
    description = ["List all available skills across plugins."],
    mixinStandardHelpOptions = true
)
class SkillListCommand : Callable<Int> {

    @Option(names = ["--plugin", "-p"], description = ["Filter by plugin name"])
    private var pluginFilter: String? = null

    @Option(names = ["--profile"], description = ["Filter by skill profile (e.g., planning, development, testing)"])
    private var profileFilter: String? = null

    @Option(names = ["--json"], description = ["Output as JSON"])
    private var jsonOutput: Boolean = false

    override fun call(): Int {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) {
            println("No plugins directory found. Run 'forge init' first.")
            return 1
        }

        val skills = mutableListOf<SkillInfo>()

        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            if (pluginFilter != null && pluginDir.name != pluginFilter) return@forEach

            val skillsDir = File(pluginDir, "skills")
            if (!skillsDir.exists()) return@forEach

            skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                val skillMd = File(skillDir, "SKILL.md")
                if (skillMd.exists()) {
                    val info = parseSkillInfo(skillMd, pluginDir.name)
                    if (profileFilter == null || info.profile.equals(profileFilter, ignoreCase = true)) {
                        skills.add(info)
                    }
                }
            }
        }

        if (skills.isEmpty()) {
            println("No skills found${pluginFilter?.let { " in plugin '$it'" } ?: ""}.")
            return 0
        }

        if (jsonOutput) {
            printSkillsJson(skills)
        } else {
            printSkillsTable(skills)
        }

        return 0
    }

    private fun parseSkillInfo(skillMd: File, pluginName: String): SkillInfo {
        val content = skillMd.readText()
        val frontmatter = extractFrontmatter(content)
        return SkillInfo(
            name = frontmatter["name"] ?: skillMd.parentFile.name,
            plugin = pluginName,
            profile = frontmatter["profile"] ?: "general",
            version = frontmatter["version"] ?: "0.0.0",
            description = frontmatter["description"] ?: "(no description)"
        )
    }

    private fun extractFrontmatter(content: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (!content.startsWith("---")) return result

        val endIndex = content.indexOf("---", 3)
        if (endIndex < 0) return result

        val frontmatter = content.substring(3, endIndex).trim()
        for (line in frontmatter.lines()) {
            val colonIdx = line.indexOf(':')
            if (colonIdx > 0) {
                val key = line.substring(0, colonIdx).trim()
                val value = line.substring(colonIdx + 1).trim().removeSurrounding("\"")
                result[key] = value
            }
        }
        return result
    }

    private fun printSkillsTable(skills: List<SkillInfo>) {
        val nameWidth = maxOf(skills.maxOf { it.name.length }, 4) + 2
        val pluginWidth = maxOf(skills.maxOf { it.plugin.length }, 6) + 2
        val profileWidth = maxOf(skills.maxOf { it.profile.length }, 7) + 2
        val versionWidth = 10

        val header = "NAME".padEnd(nameWidth) +
                "PLUGIN".padEnd(pluginWidth) +
                "PROFILE".padEnd(profileWidth) +
                "VERSION".padEnd(versionWidth) +
                "DESCRIPTION"
        println(header)
        println("-".repeat(header.length + 20))

        for (skill in skills.sortedBy { "${it.plugin}/${it.name}" }) {
            println(
                skill.name.padEnd(nameWidth) +
                        skill.plugin.padEnd(pluginWidth) +
                        skill.profile.padEnd(profileWidth) +
                        skill.version.padEnd(versionWidth) +
                        skill.description
            )
        }

        println()
        println("${skills.size} skill(s) found.")
    }

    private fun printSkillsJson(skills: List<SkillInfo>) {
        println("[")
        skills.forEachIndexed { index, skill ->
            val comma = if (index < skills.size - 1) "," else ""
            println("  {\"name\":\"${skill.name}\",\"plugin\":\"${skill.plugin}\"," +
                    "\"profile\":\"${skill.profile}\",\"version\":\"${skill.version}\"," +
                    "\"description\":\"${skill.description}\"}$comma")
        }
        println("]")
    }

    private data class SkillInfo(
        val name: String,
        val plugin: String,
        val profile: String,
        val version: String,
        val description: String
    )
}

@Command(
    name = "show",
    description = ["Show detailed information about a specific skill."],
    mixinStandardHelpOptions = true
)
class SkillShowCommand : Callable<Int> {

    @Parameters(index = "0", description = ["Skill name (e.g., 'code-review' or 'forge-superagent/code-review')"])
    private lateinit var skillName: String

    override fun call(): Int {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) {
            println("No plugins directory found.")
            return 1
        }

        val parts = skillName.split("/")
        val targetPlugin = if (parts.size == 2) parts[0] else null
        val targetSkill = if (parts.size == 2) parts[1] else parts[0]

        var found = false
        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            if (targetPlugin != null && pluginDir.name != targetPlugin) return@forEach
            val skillDir = File(pluginDir, "skills/$targetSkill")
            val skillMd = File(skillDir, "SKILL.md")
            if (skillMd.exists()) {
                found = true
                println("=== ${pluginDir.name}/$targetSkill ===")
                println()
                println(skillMd.readText())
            }
        }

        if (!found) {
            println("Skill '$skillName' not found. Use 'forge skill list' to see available skills.")
            return 1
        }

        return 0
    }
}

@Command(
    name = "validate",
    description = ["Validate skill structure and frontmatter across all plugins."],
    mixinStandardHelpOptions = true
)
class SkillValidateCommand : Callable<Int> {

    @Option(names = ["--strict"], description = ["Enable strict validation (all warnings become errors)"])
    private var strict: Boolean = false

    override fun call(): Int {
        val pluginsDir = File("plugins")
        if (!pluginsDir.exists()) {
            println("No plugins directory found.")
            return 1
        }

        var totalSkills = 0
        var errors = 0
        var warnings = 0

        pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pluginDir ->
            val skillsDir = File(pluginDir, "skills")
            if (!skillsDir.exists()) return@forEach

            skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { skillDir ->
                totalSkills++
                val skillMd = File(skillDir, "SKILL.md")
                if (!skillMd.exists()) {
                    println("[ERROR] ${pluginDir.name}/${skillDir.name}: Missing SKILL.md")
                    errors++
                    return@forEach
                }

                val content = skillMd.readText()

                // Check frontmatter
                if (!content.startsWith("---")) {
                    println("[ERROR] ${pluginDir.name}/${skillDir.name}: Missing YAML frontmatter")
                    errors++
                } else {
                    val requiredFields = listOf("name", "version", "profile")
                    for (field in requiredFields) {
                        if (!content.contains("$field:")) {
                            println("[ERROR] ${pluginDir.name}/${skillDir.name}: Missing frontmatter field '$field'")
                            errors++
                        }
                    }
                }

                // Check required sections
                val requiredSections = listOf("## Purpose", "## Instructions")
                for (section in requiredSections) {
                    if (!content.contains(section)) {
                        val level = if (strict) "ERROR" else "WARN"
                        println("[$level] ${pluginDir.name}/${skillDir.name}: Missing section '$section'")
                        if (strict) errors++ else warnings++
                    }
                }
            }
        }

        println()
        println("Validated $totalSkills skill(s): $errors error(s), $warnings warning(s)")
        return if (errors > 0) 1 else 0
    }
}
