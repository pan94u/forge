package com.forge.cli.commands

import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Callable

@Command(
    name = "mcp",
    description = ["Check MCP server status and connectivity."],
    mixinStandardHelpOptions = true,
    subcommands = [
        McpStatusCommand::class,
        McpTestCommand::class
    ]
)
class McpCommand : Runnable {
    override fun run() {
        println("Use 'forge mcp status|test'. Run 'forge mcp --help' for details.")
    }
}

@Command(
    name = "status",
    description = ["Show status of all configured MCP servers."],
    mixinStandardHelpOptions = true
)
class McpStatusCommand : Callable<Int> {

    @Option(names = ["--json"], description = ["Output as JSON"])
    private var jsonOutput: Boolean = false

    override fun call(): Int {
        val mcpConfigFile = File(".mcp.json")
        if (!mcpConfigFile.exists()) {
            println("No .mcp.json found. Run 'forge init' to create one.")
            return 1
        }

        val content = mcpConfigFile.readText()
        val serverNames = extractServerNames(content)

        if (serverNames.isEmpty()) {
            println("No MCP servers configured in .mcp.json")
            return 0
        }

        println("MCP Server Status")
        println("==================")
        println()

        val defaultPorts = mapOf(
            "forge-knowledge" to 8081,
            "forge-database" to 8082,
            "forge-service-graph" to 8083,
            "forge-artifact" to 8084,
            "forge-observability" to 8085
        )

        for (serverName in serverNames) {
            val port = defaultPorts[serverName]
            val status = if (port != null) {
                checkServerHealth("http://localhost:$port")
            } else {
                ServerStatus.UNKNOWN
            }

            val statusStr = when (status) {
                ServerStatus.HEALTHY -> "[UP]    "
                ServerStatus.UNHEALTHY -> "[DOWN]  "
                ServerStatus.UNKNOWN -> "[????]  "
            }

            val portStr = port?.let { ":$it" } ?: "(unknown port)"
            println("  $statusStr $serverName $portStr")
        }

        println()
        println("Note: Servers must be running for status checks to work.")
        println("Start servers with: docker-compose -f infrastructure/docker/docker-compose.yml up")

        return 0
    }

    private fun extractServerNames(jsonContent: String): List<String> {
        val names = mutableListOf<String>()
        val regex = Regex("\"(forge-[a-z-]+)\"\\s*:")
        for (match in regex.findAll(jsonContent)) {
            val name = match.groupValues[1]
            if (name !in names) {
                names.add(name)
            }
        }
        return names
    }

    private fun checkServerHealth(baseUrl: String): ServerStatus {
        return try {
            val url = URI("$baseUrl/health").toURL()
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            val responseCode = connection.responseCode
            connection.disconnect()
            if (responseCode in 200..299) ServerStatus.HEALTHY else ServerStatus.UNHEALTHY
        } catch (e: Exception) {
            ServerStatus.UNHEALTHY
        }
    }

    private enum class ServerStatus {
        HEALTHY, UNHEALTHY, UNKNOWN
    }
}

@Command(
    name = "test",
    description = ["Test MCP server connectivity by invoking a sample tool call."],
    mixinStandardHelpOptions = true
)
class McpTestCommand : Callable<Int> {

    @Option(names = ["--server", "-s"], description = ["Specific server to test (default: all)"])
    private var serverName: String? = null

    @Option(names = ["--timeout", "-t"], description = ["Connection timeout in seconds (default: 10)"])
    private var timeout: Int = 10

    override fun call(): Int {
        val defaultPorts = mapOf(
            "forge-knowledge" to 8081,
            "forge-database" to 8082,
            "forge-service-graph" to 8083,
            "forge-artifact" to 8084,
            "forge-observability" to 8085
        )

        val serversToTest = if (serverName != null) {
            val port = defaultPorts[serverName]
            if (port == null) {
                println("Unknown server: $serverName")
                println("Available servers: ${defaultPorts.keys.joinToString(", ")}")
                return 1
            }
            mapOf(serverName!! to port)
        } else {
            defaultPorts
        }

        println("Testing MCP Server Connectivity (timeout: ${timeout}s)")
        println("=====================================================")
        println()

        var allPassed = true

        for ((name, port) in serversToTest) {
            print("  Testing $name... ")

            val healthResult = testEndpoint("http://localhost:$port/health", timeout * 1000)
            val toolsResult = testEndpoint("http://localhost:$port/mcp/tools", timeout * 1000)

            if (healthResult.success && toolsResult.success) {
                println("OK (health: ${healthResult.latencyMs}ms, tools: ${toolsResult.latencyMs}ms)")
            } else if (healthResult.success) {
                println("PARTIAL (health: OK, tools: ${toolsResult.error})")
                allPassed = false
            } else {
                println("FAIL (${healthResult.error})")
                allPassed = false
            }
        }

        println()
        return if (allPassed) {
            println("All servers passed connectivity test.")
            0
        } else {
            println("Some servers failed. Ensure they are running.")
            1
        }
    }

    private data class TestResult(
        val success: Boolean,
        val latencyMs: Long = 0,
        val error: String? = null
    )

    private fun testEndpoint(url: String, timeoutMs: Int): TestResult {
        return try {
            val startTime = System.currentTimeMillis()
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            val responseCode = connection.responseCode
            val latency = System.currentTimeMillis() - startTime
            connection.disconnect()
            if (responseCode in 200..299) {
                TestResult(success = true, latencyMs = latency)
            } else {
                TestResult(success = false, error = "HTTP $responseCode")
            }
        } catch (e: java.net.ConnectException) {
            TestResult(success = false, error = "Connection refused")
        } catch (e: java.net.SocketTimeoutException) {
            TestResult(success = false, error = "Timeout")
        } catch (e: Exception) {
            TestResult(success = false, error = e.message ?: "Unknown error")
        }
    }
}
