package com.forge.webide.service

import com.forge.webide.model.McpContent
import com.forge.webide.model.McpTool
import com.forge.webide.model.McpToolCallResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.util.concurrent.ConcurrentHashMap

/**
 * Proxies MCP (Model Context Protocol) tool calls to configured MCP servers.
 * Handles tool discovery, invocation, and result parsing.
 */
@Service
class McpProxyService {

    private val logger = LoggerFactory.getLogger(McpProxyService::class.java)

    @Value("\${forge.mcp.servers:}")
    private var mcpServerUrls: String = ""

    private val toolCache = ConcurrentHashMap<String, List<McpTool>>()
    private val serverClients = ConcurrentHashMap<String, WebClient>()

    /**
     * List all available tools from all connected MCP servers.
     */
    fun listTools(): List<McpTool> {
        val servers = parseMcpServers()
        if (servers.isEmpty()) {
            return getDefaultTools()
        }

        val allTools = mutableListOf<McpTool>()

        servers.forEach { serverUrl ->
            try {
                val cached = toolCache[serverUrl]
                if (cached != null) {
                    allTools.addAll(cached)
                    return@forEach
                }

                val client = getOrCreateClient(serverUrl)
                val response = client.post()
                    .uri("/tools/list")
                    .retrieve()
                    .bodyToMono(Map::class.java)
                    .block()

                @Suppress("UNCHECKED_CAST")
                val tools = (response?.get("tools") as? List<Map<String, Any>>)?.map { toolMap ->
                    McpTool(
                        name = toolMap["name"] as? String ?: "",
                        description = toolMap["description"] as? String ?: "",
                        inputSchema = toolMap["inputSchema"] as? Map<String, Any?> ?: emptyMap()
                    )
                } ?: emptyList()

                toolCache[serverUrl] = tools
                allTools.addAll(tools)

                logger.debug("Discovered ${tools.size} tools from $serverUrl")
            } catch (e: Exception) {
                logger.warn("Failed to list tools from $serverUrl: ${e.message}")
            }
        }

        return allTools.ifEmpty { getDefaultTools() }
    }

    /**
     * Call an MCP tool by name with the given arguments.
     */
    fun callTool(toolName: String, arguments: Map<String, Any?>): McpToolCallResponse {
        val servers = parseMcpServers()

        // Find which server has this tool
        for (serverUrl in servers) {
            val tools = toolCache[serverUrl]
            if (tools != null && tools.any { it.name == toolName }) {
                return callToolOnServer(serverUrl, toolName, arguments)
            }
        }

        // If no server found, try all servers
        for (serverUrl in servers) {
            try {
                return callToolOnServer(serverUrl, toolName, arguments)
            } catch (e: Exception) {
                logger.debug("Tool $toolName not found on $serverUrl")
            }
        }

        // Fallback for default tools
        return handleDefaultTool(toolName, arguments)
    }

    /**
     * Invalidate the tool cache for all servers.
     */
    fun invalidateCache() {
        toolCache.clear()
        logger.info("MCP tool cache invalidated")
    }

    private fun callToolOnServer(
        serverUrl: String,
        toolName: String,
        arguments: Map<String, Any?>
    ): McpToolCallResponse {
        val client = getOrCreateClient(serverUrl)

        try {
            val response = client.post()
                .uri("/tools/call")
                .bodyValue(mapOf(
                    "name" to toolName,
                    "arguments" to arguments
                ))
                .retrieve()
                .bodyToMono(Map::class.java)
                .block()

            @Suppress("UNCHECKED_CAST")
            val content = (response?.get("content") as? List<Map<String, Any?>>)?.map { contentMap ->
                McpContent(
                    type = contentMap["type"] as? String ?: "text",
                    text = contentMap["text"] as? String,
                    data = contentMap["data"] as? String,
                    mimeType = contentMap["mimeType"] as? String,
                    uri = contentMap["uri"] as? String
                )
            } ?: emptyList()

            val isError = response?.get("isError") as? Boolean ?: false

            return McpToolCallResponse(content = content, isError = isError)
        } catch (e: Exception) {
            logger.error("MCP tool call failed: server=$serverUrl, tool=$toolName: ${e.message}")
            return McpToolCallResponse(
                content = listOf(McpContent(type = "text", text = "Error calling tool: ${e.message}")),
                isError = true
            )
        }
    }

    private fun handleDefaultTool(
        toolName: String,
        arguments: Map<String, Any?>
    ): McpToolCallResponse {
        return when (toolName) {
            "search_knowledge" -> {
                val query = arguments["query"] as? String ?: ""
                McpToolCallResponse(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Searched knowledge base for: '$query'. Configure MCP servers for real results."
                    ))
                )
            }
            "read_file" -> {
                val path = arguments["path"] as? String ?: ""
                McpToolCallResponse(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Read file: $path. Configure MCP servers for real file access."
                    ))
                )
            }
            "get_service_info" -> {
                val service = arguments["service"] as? String ?: ""
                McpToolCallResponse(
                    content = listOf(McpContent(
                        type = "text",
                        text = "Service info for: $service. Configure MCP servers for real service data."
                    ))
                )
            }
            else -> McpToolCallResponse(
                content = listOf(McpContent(
                    type = "text",
                    text = "Unknown tool: $toolName. Available tools: search_knowledge, read_file, get_service_info"
                )),
                isError = true
            )
        }
    }

    private fun getDefaultTools(): List<McpTool> {
        return listOf(
            McpTool(
                name = "search_knowledge",
                description = "Search the knowledge base for documentation, ADRs, runbooks, and API docs",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "query" to mapOf("type" to "string", "description" to "Search query"),
                        "type" to mapOf("type" to "string", "description" to "Document type filter", "enum" to listOf("wiki", "adr", "runbook", "api-doc"))
                    ),
                    "required" to listOf("query")
                )
            ),
            McpTool(
                name = "read_file",
                description = "Read a file from the workspace",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "path" to mapOf("type" to "string", "description" to "File path relative to workspace root")
                    ),
                    "required" to listOf("path")
                )
            ),
            McpTool(
                name = "get_service_info",
                description = "Get information about a service including health, dependencies, and API endpoints",
                inputSchema = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "service" to mapOf("type" to "string", "description" to "Service name or ID")
                    ),
                    "required" to listOf("service")
                )
            )
        )
    }

    private fun parseMcpServers(): List<String> {
        return mcpServerUrls
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun getOrCreateClient(serverUrl: String): WebClient {
        return serverClients.getOrPut(serverUrl) {
            WebClient.builder()
                .baseUrl(serverUrl)
                .defaultHeader("Content-Type", "application/json")
                .build()
        }
    }

    companion object {
        /**
         * Format an MCP tool call result into a readable string.
         */
        fun formatResult(result: McpToolCallResponse): String {
            if (result.isError) {
                val errorText = result.content
                    .filter { it.type == "text" }
                    .mapNotNull { it.text }
                    .joinToString("\n")
                return "Error: ${errorText.ifEmpty { "Unknown error" }}"
            }

            return result.content.joinToString("\n") { content ->
                when (content.type) {
                    "text" -> content.text ?: ""
                    "image" -> "[Image: ${content.mimeType ?: "image"}]"
                    "resource" -> "[Resource: ${content.uri ?: "unknown"}]"
                    else -> ""
                }
            }
        }
    }
}
