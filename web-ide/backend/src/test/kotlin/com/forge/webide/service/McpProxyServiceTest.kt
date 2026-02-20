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
    private val workspaceService = WorkspaceService()

    private lateinit var service: McpProxyService

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        service = McpProxyService(baselineService, dataSource, workspaceService)
    }

    // --- Default Tool Tests ---

    @Test
    fun `listTools returns default tools when no servers configured`() {
        val tools = service.listTools()

        assertThat(tools).hasSizeGreaterThanOrEqualTo(9)
        assertThat(tools.map { it.name }).contains(
            "search_knowledge",
            "read_file",
            "get_service_info",
            "run_baseline",
            "query_schema",
            "list_baselines",
            "workspace_write_file",
            "workspace_read_file",
            "workspace_list_files"
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
    fun `callTool search_knowledge with empty query does not reject as missing param`() {
        val result = service.callTool("search_knowledge", mapOf("query" to ""))

        // Empty query is valid — used by Context Picker to list all documents.
        // In test env, knowledge-base dir may not exist (returns dir-not-found error),
        // but the important thing is it doesn't reject empty query as "required".
        if (result.isError) {
            assertThat(result.content[0].text).doesNotContain("required")
        }
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
        assertThat(tools).hasSizeGreaterThanOrEqualTo(9)
    }

    // --- Workspace Tool Tests ---

    @Test
    fun `workspace_write_file creates a file in workspace`() {
        val ws = workspaceService.createWorkspace(
            com.forge.webide.model.CreateWorkspaceRequest(name = "test-ws"), "testuser"
        )

        val result = service.callTool("workspace_write_file", mapOf(
            "path" to "hello.ts",
            "content" to "console.log('hello')"
        ), ws.id)

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).contains("hello.ts")

        val content = workspaceService.getFileContent(ws.id, "hello.ts")
        assertThat(content).isEqualTo("console.log('hello')")
    }

    @Test
    fun `workspace_write_file requires path parameter`() {
        val result = service.callTool("workspace_write_file", mapOf(
            "content" to "some content"
        ), "ws-123")

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("path")
    }

    @Test
    fun `workspace_write_file requires content parameter`() {
        val result = service.callTool("workspace_write_file", mapOf(
            "path" to "test.txt"
        ), "ws-123")

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("content")
    }

    @Test
    fun `workspace_write_file blocks path traversal`() {
        val result = service.callTool("workspace_write_file", mapOf(
            "path" to "../../../etc/shadow",
            "content" to "malicious"
        ), "ws-123")

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).containsIgnoringCase("path traversal")
    }

    @Test
    fun `workspace_read_file returns file content`() {
        val ws = workspaceService.createWorkspace(
            com.forge.webide.model.CreateWorkspaceRequest(name = "test-ws-read"), "testuser"
        )
        workspaceService.createFile(ws.id, "data.json", "{\"key\":\"value\"}")

        val result = service.callTool("workspace_read_file", mapOf(
            "path" to "data.json"
        ), ws.id)

        assertThat(result.isError).isFalse()
        assertThat(result.content[0].text).isEqualTo("{\"key\":\"value\"}")
    }

    @Test
    fun `workspace_read_file returns error for missing file`() {
        val ws = workspaceService.createWorkspace(
            com.forge.webide.model.CreateWorkspaceRequest(name = "test-ws-miss"), "testuser"
        )

        val result = service.callTool("workspace_read_file", mapOf(
            "path" to "nonexistent.txt"
        ), ws.id)

        assertThat(result.isError).isTrue()
        assertThat(result.content[0].text).contains("not found")
    }

    @Test
    fun `workspace_list_files returns file tree`() {
        val ws = workspaceService.createWorkspace(
            com.forge.webide.model.CreateWorkspaceRequest(name = "test-ws-list"), "testuser"
        )

        val result = service.callTool("workspace_list_files", emptyMap(), ws.id)

        assertThat(result.isError).isFalse()
        // Default workspace has files like src/index.ts, package.json, etc.
        assertThat(result.content[0].text).contains("src")
        assertThat(result.content[0].text).contains("package.json")
    }

    @Test
    fun `workspace tools without workspaceId fall through to regular callTool`() {
        val result = service.callTool("workspace_write_file", mapOf(
            "path" to "test.txt",
            "content" to "test"
        ), null)

        // Without workspaceId, it should fall through to regular callTool which returns unknown tool
        assertThat(result.isError).isTrue()
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
