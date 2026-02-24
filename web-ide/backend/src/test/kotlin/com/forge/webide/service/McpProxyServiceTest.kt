package com.forge.webide.service

import com.forge.webide.entity.WorkspaceEntity
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpToolCallResponse
import com.forge.webide.repository.WorkspaceRepository
import com.forge.webide.service.skill.SkillCategory
import com.forge.webide.service.skill.SkillDefinition
import com.forge.webide.service.skill.SkillLoader
import com.forge.webide.service.skill.SkillScript
import com.forge.webide.service.skill.SkillSubFile
import com.forge.webide.service.skill.SkillContentType
import com.forge.webide.repository.SkillUsageRepository
import com.forge.webide.service.memory.SessionSummaryService
import com.forge.webide.service.memory.WorkspaceMemoryService
import com.forge.webide.service.skill.SkillQualityHookService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
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
    private val skillLoader = mockk<SkillLoader>(relaxed = true)
    private val skillUsageRepository = mockk<SkillUsageRepository>(relaxed = true)
    private val workspaceRepository = mockk<WorkspaceRepository>(relaxed = true)
    private val gitService = mockk<GitService>(relaxed = true)
    private val workspaceMemoryService = mockk<WorkspaceMemoryService>(relaxed = true)
    private val sessionSummaryService = mockk<SessionSummaryService>(relaxed = true)
    private val knowledgeIndexService = mockk<KnowledgeIndexService>(relaxed = true)
    private val skillQualityHookService = mockk<SkillQualityHookService>(relaxed = true)

    private lateinit var workspaceService: WorkspaceService
    private lateinit var service: McpProxyService

    @TempDir
    lateinit var tempDir: Path

    // In-memory store for mocked repository
    private val entityStore = mutableMapOf<String, WorkspaceEntity>()

    @BeforeEach
    fun setup() {
        entityStore.clear()

        // Mock repository save: capture and store entity
        val entitySlot = slot<WorkspaceEntity>()
        every { workspaceRepository.save(capture(entitySlot)) } answers {
            val entity = entitySlot.captured
            entityStore[entity.id] = entity
            entity
        }
        every { workspaceRepository.findById(any()) } answers {
            val id = firstArg<String>()
            Optional.ofNullable(entityStore[id])
        }
        every { workspaceRepository.count() } answers { entityStore.size.toLong() }

        workspaceService = WorkspaceService(workspaceRepository, gitService, tempDir.toString())
        workspaceService.init()

        // Build handler beans
        val builtinToolHandler = BuiltinToolHandler(baselineService, dataSource, knowledgeIndexService)
        val workspaceToolHandler = WorkspaceToolHandler(workspaceService)
        val skillToolHandler = SkillToolHandler(skillLoader, skillUsageRepository, skillQualityHookService)
        val memoryToolHandler = MemoryToolHandler(workspaceMemoryService, sessionSummaryService, workspaceService)

        service = McpProxyService(builtinToolHandler, workspaceToolHandler, skillToolHandler, memoryToolHandler)
    }

    // --- Default Tool Tests ---

    @Test
    fun `listTools returns default tools when no servers configured`() {
        val tools = service.listTools()

        assertThat(tools).hasSizeGreaterThanOrEqualTo(14) // 9 original + 3 skill tools + 2 memory tools
        assertThat(tools.map { it.name }).contains(
            "search_knowledge",
            "read_file",
            "get_service_info",
            "run_baseline",
            "query_schema",
            "list_baselines",
            "workspace_write_file",
            "workspace_read_file",
            "workspace_list_files",
            "read_skill",
            "run_skill_script",
            "list_skills"
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
        assertThat(tools).hasSizeGreaterThanOrEqualTo(12)
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
        assertThat(result.content[0].text).contains("src")
        assertThat(result.content[0].text).contains("package.json")
    }

    @Test
    fun `workspace tools without workspaceId fall through to regular callTool`() {
        val result = service.callTool("workspace_write_file", mapOf(
            "path" to "test.txt",
            "content" to "test"
        ), null)

        assertThat(result.isError).isTrue()
    }

    // --- Skill Tool Tests (Phase 4) ---

    @Nested
    inner class ReadSkillTool {

        @Test
        fun `read_skill returns SKILL_md content for valid skill`() {
            every { skillLoader.loadSkill("code-generation") } returns SkillDefinition(
                name = "code-generation",
                description = "Code generation skill",
                content = "# Code Generation\n\nDesign-first approach.",
                sourcePath = "${tempDir}/forge-superagent/skills/code-generation/SKILL.md",
                category = SkillCategory.DELIVERY
            )

            val result = service.callTool("read_skill", mapOf("skill_name" to "code-generation"))

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("Code Generation")
            assertThat(result.content[0].text).contains("Design-first approach")
        }

        @Test
        fun `read_skill returns error for unknown skill`() {
            every { skillLoader.loadSkill("nonexistent") } returns null

            val result = service.callTool("read_skill", mapOf("skill_name" to "nonexistent"))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("not found")
        }

        @Test
        fun `read_skill requires skill_name parameter`() {
            val result = service.callTool("read_skill", emptyMap())

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("skill_name")
        }

        @Test
        fun `read_skill prevents path traversal in file parameter`() {
            val result = service.callTool("read_skill", mapOf(
                "skill_name" to "code-generation",
                "file" to "../../../etc/passwd"
            ))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).containsIgnoringCase("path traversal")
        }

        @Test
        fun `read_skill reads sub-file from skill directory`() {
            // Create a skill directory with a sub-file
            val skillDir = tempDir.resolve("test-skill")
            Files.createDirectories(skillDir.resolve("examples"))
            Files.writeString(skillDir.resolve("SKILL.md"), "# Test Skill")
            Files.writeString(skillDir.resolve("examples/pattern.md"), "# Pattern Example\n\nSample code here.")

            every { skillLoader.loadSkill("test-skill") } returns SkillDefinition(
                name = "test-skill",
                description = "Test",
                content = "# Test Skill",
                sourcePath = "${skillDir}/SKILL.md",
                subFiles = listOf(SkillSubFile("examples/pattern.md", "Pattern example", SkillContentType.EXAMPLE))
            )

            val result = service.callTool("read_skill", mapOf(
                "skill_name" to "test-skill",
                "file" to "examples/pattern.md"
            ))

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("Pattern Example")
            assertThat(result.content[0].text).contains("Sample code here")
        }
    }

    @Nested
    inner class ListSkillsTool {

        @Test
        fun `list_skills returns all skills metadata`() {
            every { skillLoader.loadSkillMetadataCatalog(null, null, null) } returns listOf(
                SkillDefinition(
                    name = "kotlin-conventions",
                    description = "Kotlin conventions",
                    content = "",
                    sourcePath = "test",
                    category = SkillCategory.FOUNDATION,
                    version = "1.0"
                ),
                SkillDefinition(
                    name = "code-generation",
                    description = "Code generation",
                    content = "",
                    sourcePath = "test",
                    category = SkillCategory.DELIVERY,
                    scripts = listOf(SkillScript("scripts/check.py", "Check code", "python"))
                )
            )

            val result = service.callTool("list_skills", emptyMap())

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("kotlin-conventions")
            assertThat(result.content[0].text).contains("code-generation")
            assertThat(result.content[0].text).contains("foundation")
            assertThat(result.content[0].text).contains("delivery")
            assertThat(result.content[0].text).contains("scripts/check.py")
        }

        @Test
        fun `list_skills filters by category`() {
            every { skillLoader.loadSkillMetadataCatalog(null, SkillCategory.FOUNDATION, null) } returns listOf(
                SkillDefinition(
                    name = "kotlin-conventions",
                    description = "Kotlin conventions",
                    content = "",
                    sourcePath = "test",
                    category = SkillCategory.FOUNDATION
                )
            )

            val result = service.callTool("list_skills", mapOf("category" to "FOUNDATION"))

            assertThat(result.isError).isFalse()
            assertThat(result.content[0].text).contains("kotlin-conventions")
            assertThat(result.content[0].text).contains("1") // count
        }

        @Test
        fun `list_skills returns error for invalid category`() {
            val result = service.callTool("list_skills", mapOf("category" to "INVALID"))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("Invalid category")
        }
    }

    @Nested
    inner class RunSkillScriptTool {

        @Test
        fun `run_skill_script requires skill_name parameter`() {
            val result = service.callTool("run_skill_script", mapOf("script" to "scripts/check.py"))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("skill_name")
        }

        @Test
        fun `run_skill_script requires script parameter`() {
            val result = service.callTool("run_skill_script", mapOf("skill_name" to "test"))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("script")
        }

        @Test
        fun `run_skill_script prevents path traversal`() {
            val result = service.callTool("run_skill_script", mapOf(
                "skill_name" to "test",
                "script" to "../../../malicious.py"
            ))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).containsIgnoringCase("path traversal")
        }

        @Test
        fun `run_skill_script returns error for unknown skill`() {
            every { skillLoader.loadSkill("nonexistent") } returns null

            val result = service.callTool("run_skill_script", mapOf(
                "skill_name" to "nonexistent",
                "script" to "scripts/check.py"
            ))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("not found")
        }

        @Test
        fun `run_skill_script returns error when script not in skill definition`() {
            every { skillLoader.loadSkill("test-skill") } returns SkillDefinition(
                name = "test-skill",
                description = "Test",
                content = "",
                sourcePath = "${tempDir}/test/SKILL.md",
                scripts = emptyList()
            )

            val result = service.callTool("run_skill_script", mapOf(
                "skill_name" to "test-skill",
                "script" to "scripts/unknown.py"
            ))

            assertThat(result.isError).isTrue()
            assertThat(result.content[0].text).contains("not found")
        }
    }

    // --- Skill Tool Definition Tests ---

    @Test
    fun `read_skill tool has proper schema`() {
        val tools = service.listTools()
        val readSkill = tools.first { it.name == "read_skill" }

        assertThat(readSkill.description).contains("SKILL.md")
        @Suppress("UNCHECKED_CAST")
        val properties = readSkill.inputSchema["properties"] as Map<String, Any?>
        assertThat(properties).containsKey("skill_name")
        assertThat(properties).containsKey("file")
    }

    @Test
    fun `run_skill_script tool has proper schema`() {
        val tools = service.listTools()
        val runScript = tools.first { it.name == "run_skill_script" }

        assertThat(runScript.description).contains("script")
        @Suppress("UNCHECKED_CAST")
        val properties = runScript.inputSchema["properties"] as Map<String, Any?>
        assertThat(properties).containsKey("skill_name")
        assertThat(properties).containsKey("script")
        assertThat(properties).containsKey("args")
    }

    @Test
    fun `list_skills tool has proper schema`() {
        val tools = service.listTools()
        val listSkills = tools.first { it.name == "list_skills" }

        assertThat(listSkills.description).contains("metadata")
        @Suppress("UNCHECKED_CAST")
        val properties = listSkills.inputSchema["properties"] as Map<String, Any?>
        assertThat(properties).containsKey("profile")
        assertThat(properties).containsKey("category")
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
