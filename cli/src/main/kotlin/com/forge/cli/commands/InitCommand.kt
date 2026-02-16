package com.forge.cli.commands

import com.forge.cli.ForgeCli
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import picocli.CommandLine.ParentCommand
import java.io.File
import java.util.concurrent.Callable

@Command(
    name = "init",
    description = ["Initialize a Forge project with CLAUDE.md, .mcp.json, and plugin references."],
    mixinStandardHelpOptions = true
)
class InitCommand : Callable<Int> {

    @ParentCommand
    private lateinit var parent: ForgeCli

    @Parameters(index = "0", defaultValue = ".", description = ["Target directory (default: current directory)"])
    private lateinit var targetDir: String

    @Option(names = ["--template", "-t"], description = ["Project template: backend-api, data-platform, mobile, fullstack"])
    private var template: String = "backend-api"

    @Option(names = ["--org"], description = ["Organization name for CLAUDE.md header"])
    private var orgName: String? = null

    @Option(names = ["--team"], description = ["Team name for CLAUDE.md header"])
    private var teamName: String? = null

    @Option(names = ["--force", "-f"], description = ["Overwrite existing configuration files"])
    private var force: Boolean = false

    @Option(names = ["--with-knowledge"], description = ["Include forge-knowledge plugin reference"])
    private var withKnowledge: Boolean = false

    @Option(names = ["--with-deployment"], description = ["Include forge-deployment plugin reference"])
    private var withDeployment: Boolean = false

    override fun call(): Int {
        val dir = File(targetDir).absoluteFile
        println("Initializing Forge project in: ${dir.path}")
        println("Template: $template")

        if (!dir.exists()) {
            dir.mkdirs()
            println("  Created directory: ${dir.path}")
        }

        val claudeMdFile = File(dir, ForgeCli.CLAUDE_MD)
        if (claudeMdFile.exists() && !force) {
            println("  CLAUDE.md already exists (use --force to overwrite)")
        } else {
            claudeMdFile.writeText(generateClaudeMd())
            println("  Created CLAUDE.md")
        }

        val mcpJsonFile = File(dir, ForgeCli.MCP_JSON)
        if (mcpJsonFile.exists() && !force) {
            println("  .mcp.json already exists (use --force to overwrite)")
        } else {
            mcpJsonFile.writeText(generateMcpJson())
            println("  Created .mcp.json")
        }

        val forgeConfigFile = File(dir, ForgeCli.CONFIG_FILE)
        if (forgeConfigFile.exists() && !force) {
            println("  .forge.json already exists (use --force to overwrite)")
        } else {
            forgeConfigFile.writeText(generateForgeConfig())
            println("  Created .forge.json")
        }

        println()
        println("Forge project initialized successfully!")
        println()
        println("Next steps:")
        println("  1. Review and customize CLAUDE.md for your project")
        println("  2. Run 'forge doctor' to verify your environment")
        println("  3. Run 'forge skill list' to see available skills")

        return 0
    }

    private fun generateClaudeMd(): String {
        val orgHeader = orgName?.let { "# $it" } ?: "# My Organization"
        val teamHeader = teamName?.let { "## $it" } ?: "## My Team"

        return buildString {
            appendLine(orgHeader)
            appendLine()
            appendLine(teamHeader)
            appendLine()
            appendLine("## Project Overview")
            appendLine()
            appendLine("<!-- Describe your project purpose and architecture here -->")
            appendLine()
            appendLine("## Build & Run")
            appendLine()
            appendLine("```bash")
            when (template) {
                "backend-api" -> {
                    appendLine("# Build")
                    appendLine("./gradlew build")
                    appendLine()
                    appendLine("# Run")
                    appendLine("./gradlew bootRun")
                    appendLine()
                    appendLine("# Test")
                    appendLine("./gradlew test")
                }
                "data-platform" -> {
                    appendLine("# Build")
                    appendLine("./gradlew build")
                    appendLine()
                    appendLine("# Run pipeline")
                    appendLine("./gradlew runPipeline")
                    appendLine()
                    appendLine("# Test")
                    appendLine("./gradlew test")
                }
                "mobile" -> {
                    appendLine("# Install dependencies")
                    appendLine("npm install")
                    appendLine()
                    appendLine("# Run (iOS)")
                    appendLine("npx react-native run-ios")
                    appendLine()
                    appendLine("# Run (Android)")
                    appendLine("npx react-native run-android")
                    appendLine()
                    appendLine("# Test")
                    appendLine("npm test")
                }
                "fullstack" -> {
                    appendLine("# Backend")
                    appendLine("./gradlew :backend:bootRun")
                    appendLine()
                    appendLine("# Frontend")
                    appendLine("cd frontend && npm run dev")
                    appendLine()
                    appendLine("# Test all")
                    appendLine("./gradlew test && cd frontend && npm test")
                }
                else -> {
                    appendLine("# Build")
                    appendLine("./gradlew build")
                }
            }
            appendLine("```")
            appendLine()
            appendLine("## Coding Conventions")
            appendLine()
            appendLine("<!-- Add your team's coding conventions here -->")
            appendLine()
            appendLine("## Security Rules")
            appendLine()
            appendLine("- NEVER hardcode credentials; use environment variables")
            appendLine("- NEVER commit secrets to version control")
            appendLine("- All API endpoints require authentication")
            appendLine()
            appendLine("## Active Forge Plugins")
            appendLine()
            appendLine("- forge-foundation (core skills and profiles)")
            appendLine("- forge-superagent (SuperAgent skill profiles)")
            if (withKnowledge) appendLine("- forge-knowledge (internal knowledge access)")
            if (withDeployment) appendLine("- forge-deployment (deployment patterns and checklists)")
        }
    }

    private fun generateMcpJson(): String {
        return buildString {
            appendLine("{")
            appendLine("  \"mcpServers\": {")
            appendLine("    \"forge-knowledge\": {")
            appendLine("      \"command\": \"java\",")
            appendLine("      \"args\": [\"-jar\", \"mcp-servers/forge-knowledge-mcp/build/libs/forge-knowledge-mcp.jar\"],")
            appendLine("      \"env\": {")
            appendLine("        \"FORGE_KNOWLEDGE_DIR\": \"./knowledge-base\"")
            appendLine("      }")
            appendLine("    },")
            appendLine("    \"forge-database\": {")
            appendLine("      \"command\": \"java\",")
            appendLine("      \"args\": [\"-jar\", \"mcp-servers/forge-database-mcp/build/libs/forge-database-mcp.jar\"],")
            appendLine("      \"env\": {")
            appendLine("        \"DATABASE_URL\": \"\${DATABASE_URL}\"")
            appendLine("      }")
            appendLine("    },")
            appendLine("    \"forge-service-graph\": {")
            appendLine("      \"command\": \"java\",")
            appendLine("      \"args\": [\"-jar\", \"mcp-servers/forge-service-graph-mcp/build/libs/forge-service-graph-mcp.jar\"]")
            appendLine("    },")
            appendLine("    \"forge-artifact\": {")
            appendLine("      \"command\": \"java\",")
            appendLine("      \"args\": [\"-jar\", \"mcp-servers/forge-artifact-mcp/build/libs/forge-artifact-mcp.jar\"]")
            appendLine("    },")
            appendLine("    \"forge-observability\": {")
            appendLine("      \"command\": \"java\",")
            appendLine("      \"args\": [\"-jar\", \"mcp-servers/forge-observability-mcp/build/libs/forge-observability-mcp.jar\"]")
            appendLine("    }")
            appendLine("  }")
            appendLine("}")
        }
    }

    private fun generateForgeConfig(): String {
        return buildString {
            appendLine("{")
            appendLine("  \"version\": \"0.1.0\",")
            appendLine("  \"template\": \"$template\",")
            appendLine("  \"plugins\": [")
            appendLine("    \"forge-foundation\",")
            appendLine("    \"forge-superagent\"${if (withKnowledge || withDeployment) "," else ""}")
            if (withKnowledge) appendLine("    \"forge-knowledge\"${if (withDeployment) "," else ""}")
            if (withDeployment) appendLine("    \"forge-deployment\"")
            appendLine("  ],")
            appendLine("  \"mcpServers\": [")
            appendLine("    \"forge-knowledge\",")
            appendLine("    \"forge-database\",")
            appendLine("    \"forge-service-graph\",")
            appendLine("    \"forge-artifact\",")
            appendLine("    \"forge-observability\"")
            appendLine("  ],")
            appendLine("  \"settings\": {")
            appendLine("    \"autoUpdate\": true,")
            appendLine("    \"telemetry\": false")
            appendLine("  }")
            appendLine("}")
        }
    }
}
