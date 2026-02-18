package com.forge.webide.service

import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import javax.sql.DataSource

/**
 * Tests for McpProxyService.
 *
 * Focuses on built-in tool handling, cache behavior, and result formatting.
 * Uses mock BaselineService and DataSource since the focus is on tool dispatch.
 */
class McpProxyServiceTest {

    private val baselineService = mockk<BaselineService>(relaxed = true)
    private val dataSource = mockk<DataSource>(relaxed = true)

    private lateinit var service: McpProxyService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        service = McpProxyService(baselineService, dataSource)
    }

    // --- Default Tool Tests ---

    @Test
    fun `listTools returns default tools when no servers configured`() {
        val tools = service.listTools()

        assertThat(tools).hasSizeGreaterThanOrEqualTo(6)
        assertThat(tools.map { it.name }).contains(
            "search_knowledge",
            "read_file",
            "get_service_info",
            "run_baseline",
            "query_schema",
            "list_baselines"
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
    fun `callTool handles get_service_info for all services`() {
        val result = service.callTool("get_service_info", mapOf("service" to ""))

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("backend")
        assertThat(result.content[0].text).contains("frontend")
        assertThat(result.content[0].text).contains("nginx")
    }

    @Test
    fun `callTool handles get_service_info for specific service`() {
        val result = service.callTool("get_service_info", mapOf("service" to "backend"))

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("Spring Boot")
        assertThat(result.content[0].text).contains("8080")
    }

    @Test
    fun `callTool returns error for unknown tool`() {
        val result = service.callTool("nonexistent_tool", emptyMap())

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("Unknown tool")
    }

    @Test
    fun `callTool run_baseline delegates to BaselineService`() {
        every { baselineService.runBaselines(any(), any()) } returns BaselineService.BaselineReport(
            results = listOf(
                BaselineService.BaselineResult(
                    name = "code-style-baseline",
                    status = "PASS",
                    durationMs = 500,
                    output = "All checks passed",
                    errorOutput = "",
                    exitCode = 0
                )
            ),
            totalDurationMs = 500,
            timestamp = "2026-02-18T10:00:00Z",
            allPassed = true,
            summary = "Baseline Report: 1 passed, 0 failed\n  [PASS] code-style-baseline (500ms)"
        )

        val result = service.callTool("run_baseline", mapOf(
            "baselines" to listOf("code-style-baseline")
        ))

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("PASS")
    }

    @Test
    fun `callTool run_baseline reports failure correctly`() {
        every { baselineService.runBaselines(any(), any()) } returns BaselineService.BaselineReport(
            results = listOf(
                BaselineService.BaselineResult(
                    name = "security-baseline",
                    status = "FAIL",
                    durationMs = 1200,
                    output = "Found hardcoded credentials in Config.kt",
                    errorOutput = "",
                    exitCode = 1
                )
            ),
            totalDurationMs = 1200,
            timestamp = "2026-02-18T10:00:00Z",
            allPassed = false,
            summary = "Baseline Report: 0 passed, 1 failed\n  [FAIL] security-baseline (1200ms)"
        )

        val result = service.callTool("run_baseline", mapOf(
            "baselines" to listOf("security-baseline")
        ))

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("FAIL")
    }

    @Test
    fun `callTool list_baselines delegates to BaselineService`() {
        every { baselineService.listBaselines() } returns listOf(
            "code-style-baseline",
            "security-baseline",
            "test-coverage-baseline"
        )

        val result = service.callTool("list_baselines", emptyMap())

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("code-style-baseline")
        assertThat(result.content[0].text).contains("3")
    }

    @Test
    fun `callTool search_knowledge requires query param`() {
        val result = service.callTool("search_knowledge", mapOf("query" to ""))

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("required")
    }

    @Test
    fun `callTool read_file prevents path traversal`() {
        val result = service.callTool("read_file", mapOf("path" to "../../../etc/passwd"))

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("path traversal")
    }

    // --- Cache Tests ---

    @Test
    fun `invalidateCache clears the tool cache`() {
        service.listTools()
        service.invalidateCache()

        val tools = service.listTools()
        assertThat(tools).hasSizeGreaterThanOrEqualTo(6)
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
