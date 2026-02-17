package com.forge.webide.service

import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Tests for McpProxyService.
 *
 * Focuses on default tool handling, cache behavior, and result formatting
 * since MCP server integration requires a running server.
 */
class McpProxyServiceTest {

    private val service = McpProxyService()

    // --- Default Tool Tests ---

    @Test
    fun `listTools returns default tools when no servers configured`() {
        val tools = service.listTools()

        assertThat(tools).hasSize(3)
        assertThat(tools.map { it.name }).containsExactlyInAnyOrder(
            "search_knowledge",
            "read_file",
            "get_service_info"
        )
    }

    @Test
    fun `listTools default tools have proper schemas`() {
        val tools = service.listTools()
        val searchTool = tools.first { it.name == "search_knowledge" }

        assertThat(searchTool.description).isNotBlank()
        assertThat(searchTool.inputSchema).containsKey("type")
        assertThat(searchTool.inputSchema["type"]).isEqualTo("object")

        @Suppress("UNCHECKED_CAST")
        val properties = searchTool.inputSchema["properties"] as? Map<String, Any?>
        assertThat(properties).isNotNull
        assertThat(properties).containsKey("query")
    }

    @Test
    fun `callTool handles search_knowledge default tool`() {
        val result = service.callTool("search_knowledge", mapOf("query" to "spring boot"))

        assertThat(result.isError).isFalse()
        assertThat(result.content).hasSize(1)
        assertThat(result.content[0].type).isEqualTo("text")
        assertThat(result.content[0].text).contains("spring boot")
    }

    @Test
    fun `callTool handles read_file default tool`() {
        val result = service.callTool("read_file", mapOf("path" to "src/main/App.kt"))

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("src/main/App.kt")
    }

    @Test
    fun `callTool handles get_service_info default tool`() {
        val result = service.callTool("get_service_info", mapOf("service" to "order-service"))

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("order-service")
    }

    @Test
    fun `callTool returns error for unknown tool`() {
        val result = service.callTool("nonexistent_tool", emptyMap())

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("Unknown tool")
    }

    // --- Cache Tests ---

    @Test
    fun `invalidateCache clears the tool cache`() {
        // First call populates default tools
        service.listTools()

        // Invalidate
        service.invalidateCache()

        // Should still return defaults (no servers configured)
        val tools = service.listTools()
        assertThat(tools).hasSize(3)
    }

    // --- formatResult Tests ---

    @Test
    fun `formatResult formats text content`() {
        val response = McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Search result: found 3 docs")),
            isError = false
        )

        val formatted = McpProxyService.formatResult(response)
        assertThat(formatted).isEqualTo("Search result: found 3 docs")
    }

    @Test
    fun `formatResult formats multiple content items`() {
        val response = McpToolCallResponse(
            content = listOf(
                McpContent(type = "text", text = "Line 1"),
                McpContent(type = "text", text = "Line 2")
            ),
            isError = false
        )

        val formatted = McpProxyService.formatResult(response)
        assertThat(formatted).isEqualTo("Line 1\nLine 2")
    }

    @Test
    fun `formatResult formats error response`() {
        val response = McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Connection refused")),
            isError = true
        )

        val formatted = McpProxyService.formatResult(response)
        assertThat(formatted).startsWith("Error:")
        assertThat(formatted).contains("Connection refused")
    }

    @Test
    fun `formatResult handles image content`() {
        val response = McpToolCallResponse(
            content = listOf(McpContent(type = "image", mimeType = "image/png")),
            isError = false
        )

        val formatted = McpProxyService.formatResult(response)
        assertThat(formatted).contains("[Image:")
    }

    @Test
    fun `formatResult handles resource content`() {
        val response = McpToolCallResponse(
            content = listOf(McpContent(type = "resource", uri = "file:///docs/api.md")),
            isError = false
        )

        val formatted = McpProxyService.formatResult(response)
        assertThat(formatted).contains("[Resource:")
    }
}
