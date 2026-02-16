package com.forge.cli

import com.forge.cli.commands.*
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import kotlin.system.exitProcess

@Command(
    name = "forge",
    mixinStandardHelpOptions = true,
    version = ["Forge CLI 0.1.0"],
    description = [
        "Forge Platform CLI — manage skills, plugins, MCP servers, and project configuration.",
        "",
        "Forge is an AI-powered intelligent delivery platform that augments Claude Code",
        "with enterprise-grade skills, knowledge, and automation."
    ],
    subcommands = [
        InitCommand::class,
        DoctorCommand::class,
        SkillCommand::class,
        McpCommand::class,
        UpdateCommand::class,
        CommandLine.HelpCommand::class
    ]
)
class ForgeCli : Runnable {

    @Option(names = ["--verbose", "-v"], description = ["Enable verbose output"])
    var verbose: Boolean = false

    @Option(names = ["--config", "-c"], description = ["Path to forge config file"])
    var configPath: String? = null

    override fun run() {
        CommandLine(this).usage(System.out)
    }

    companion object {
        const val CONFIG_FILE = ".forge.json"
        const val CLAUDE_MD = "CLAUDE.md"
        const val MCP_JSON = ".mcp.json"
        const val PLUGIN_DIR = ".claude-plugin"
    }
}

fun main(args: Array<String>) {
    val commandLine = CommandLine(ForgeCli())
        .setExecutionStrategy(CommandLine.RunLast())
        .setCaseInsensitiveEnumValuesAllowed(true)
        .setUsageHelpAutoWidth(true)

    val exitCode = commandLine.execute(*args)
    exitProcess(exitCode)
}
