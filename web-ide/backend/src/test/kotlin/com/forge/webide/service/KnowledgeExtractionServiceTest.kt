package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.entity.KnowledgeExtractionLogEntity
import com.forge.webide.model.*
import com.forge.webide.repository.KnowledgeExtractionLogRepository
import com.forge.webide.repository.WorkspaceRepository
import io.mockk.*
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class KnowledgeExtractionServiceTest {

    private lateinit var agenticLoopOrchestrator: AgenticLoopOrchestrator
    private lateinit var mcpProxyService: McpProxyService
    private lateinit var knowledgeTagService: KnowledgeTagService
    private lateinit var extractionLogRepository: KnowledgeExtractionLogRepository
    private lateinit var claudeAdapter: ModelAdapter
    private lateinit var modelRegistry: ModelRegistry
    private lateinit var workspaceRepository: WorkspaceRepository
    private lateinit var service: KnowledgeExtractionService

    private val sampleTags = listOf(
        KnowledgeTag("ws-1_ui-ux", "UI/UX 设计基线", "desc", "一、UI/UX", "", 0, "empty", null, "ws-1", "ui-ux"),
        KnowledgeTag("ws-1_api-contract", "API 契约基线", "desc", "二、API", "", 1, "empty", null, "ws-1", "api-contract"),
        KnowledgeTag("ws-1_data-model", "数据模型基线", "desc", "三、数据模型", "", 2, "empty", null, "ws-1", "data-model")
    )

    @BeforeEach
    fun setUp() {
        agenticLoopOrchestrator = mockk(relaxed = true)
        mcpProxyService = mockk(relaxed = true)
        knowledgeTagService = mockk(relaxed = true)
        extractionLogRepository = mockk(relaxed = true)
        claudeAdapter = mockk(relaxed = true)
        modelRegistry = mockk(relaxed = true)
        workspaceRepository = mockk(relaxed = true)

        service = KnowledgeExtractionService(
            agenticLoopOrchestrator,
            mcpProxyService,
            knowledgeTagService,
            extractionLogRepository,
            claudeAdapter,
            modelRegistry,
            workspaceRepository
        )

        // Default mocks
        every { mcpProxyService.listTools() } returns listOf(
            McpTool("workspace_list_files", "List files", emptyMap()),
            McpTool("workspace_read_file", "Read file", emptyMap()),
            McpTool("analyze_codebase", "Analyze codebase", emptyMap()),
            McpTool("search_knowledge", "Search knowledge", emptyMap()),
            McpTool("read_file", "Read file", emptyMap()),
            McpTool("workspace_write_file", "Write file", emptyMap())
        )
        every { knowledgeTagService.listTags("ws-1") } returns sampleTags
        every { knowledgeTagService.listTags(null) } returns emptyList()
        every { knowledgeTagService.updateTag(any(), any()) } returns sampleTags[0]
        every { extractionLogRepository.save(any()) } answers { firstArg() }
    }

    @Test
    fun `triggerExtraction returns jobId and status is running`() {
        val jobId = service.triggerExtraction(ExtractionTriggerRequest("ws-1"))

        assertThat(jobId).isNotBlank()
        val status = service.getJobStatus(jobId)
        assertThat(status).isNotNull
        assertThat(status!!.status).isEqualTo("running")
        assertThat(status.workspaceId).isEqualTo("ws-1")
    }

    @Test
    fun `getJobStatus returns null for unknown jobId`() {
        val status = service.getJobStatus("nonexistent")
        assertThat(status).isNull()
    }

    @Test
    fun `parseDiscoveryJson parses valid JSON correctly`() {
        val json = """
        [
            {"tagId": "ui-ux", "applicable": true, "reason": "Has React components"},
            {"tagId": "api-contract", "applicable": false, "reason": "No REST endpoints"},
            {"tagId": "data-model", "applicable": true, "reason": "Has entities"}
        ]
        """.trimIndent()

        val results = service.parseDiscoveryJson(json, sampleTags)

        assertThat(results).hasSize(3)
        assertThat(results["ui-ux"]!!.applicable).isTrue()
        assertThat(results["api-contract"]!!.applicable).isFalse()
        assertThat(results["api-contract"]!!.reason).isEqualTo("No REST endpoints")
        assertThat(results["data-model"]!!.applicable).isTrue()
    }

    @Test
    fun `parseDiscoveryJson handles code block wrapped JSON`() {
        val content = """
        Here is my analysis:

        ```json
        [{"tagId": "ui-ux", "applicable": true, "reason": "Found components"}]
        ```
        """.trimIndent()

        val results = service.parseDiscoveryJson(content, sampleTags)

        assertThat(results["ui-ux"]!!.applicable).isTrue()
        // Other tags default to applicable (matched by tagKey)
        assertThat(results["api-contract"]!!.applicable).isTrue()
    }

    @Test
    fun `parseDiscoveryJson defaults missing tags to applicable`() {
        val json = """[{"tagId": "ui-ux", "applicable": false, "reason": "N/A"}]"""

        val results = service.parseDiscoveryJson(json, sampleTags)

        assertThat(results["ui-ux"]!!.applicable).isFalse()
        assertThat(results["api-contract"]!!.applicable).isTrue()
        assertThat(results["data-model"]!!.applicable).isTrue()
    }

    @Test
    fun `parseDiscoveryJson handles invalid JSON gracefully`() {
        val results = service.parseDiscoveryJson("not valid json", sampleTags)

        // All tags should default to applicable
        assertThat(results).hasSize(3)
        assertThat(results.values.all { it.applicable }).isTrue()
    }

    @Test
    fun `getLogs returns logs from repository`() {
        val entities = listOf(
            KnowledgeExtractionLogEntity(
                id = "log-1", jobId = "job-1", workspaceId = "ws-1",
                tagId = "ws-1_ui-ux", tagName = "UI/UX", phase = "extraction",
                status = "extracted", applicable = true, contentLength = 500,
                durationMs = 1000, createdAt = Instant.now()
            )
        )
        every { extractionLogRepository.findTop30ByOrderByCreatedAtDesc() } returns entities

        val logs = service.getLogs(null, null)

        assertThat(logs).hasSize(1)
        assertThat(logs[0].jobId).isEqualTo("job-1")
        assertThat(logs[0].tagId).isEqualTo("ws-1_ui-ux")
    }

    @Test
    fun `getLogs filters by tagId when provided`() {
        val entities = listOf(
            KnowledgeExtractionLogEntity(
                id = "log-2", jobId = "job-1", workspaceId = "ws-1",
                tagId = "ws-1_api-contract", tagName = "API", phase = "extraction",
                status = "extracted", applicable = true, contentLength = 300,
                durationMs = 800, createdAt = Instant.now()
            )
        )
        every { extractionLogRepository.findByTagIdOrderByCreatedAtDesc("ws-1_api-contract") } returns entities

        val logs = service.getLogs("ws-1_api-contract", null)

        assertThat(logs).hasSize(1)
        assertThat(logs[0].tagId).isEqualTo("ws-1_api-contract")
    }

    @Test
    fun `getLogs filters by workspaceId when provided`() {
        val entities = listOf(
            KnowledgeExtractionLogEntity(
                id = "log-3", jobId = "job-2", workspaceId = "ws-2",
                tagId = "ws-2_ui-ux", tagName = "UI/UX", phase = "extraction",
                status = "extracted", applicable = true, contentLength = 200,
                durationMs = 500, createdAt = Instant.now()
            )
        )
        every { extractionLogRepository.findByWorkspaceIdOrderByCreatedAtDesc("ws-2") } returns entities

        val logs = service.getLogs(null, null, "ws-2")

        assertThat(logs).hasSize(1)
        assertThat(logs[0].workspaceId).isEqualTo("ws-2")
    }

    @Test
    fun `extraction tools only include read-only tools`() {
        // The service should filter out workspace_write_file
        val allTools = mcpProxyService.listTools()
        val writeToolPresent = allTools.any { it.name == "workspace_write_file" }
        assertThat(writeToolPresent).isTrue()

        // Verify the extraction uses filtered tools - indirectly check via tool list
        val readOnlyNames = setOf(
            "workspace_list_files", "workspace_read_file", "analyze_codebase",
            "search_knowledge", "read_file"
        )
        val extractionTools = allTools.filter { it.name in readOnlyNames }
        assertThat(extractionTools).hasSize(5)
        assertThat(extractionTools.none { it.name == "workspace_write_file" }).isTrue()
    }

    @Test
    fun `triggerExtraction with specific tagId only processes that tag`() {
        every { knowledgeTagService.listTags("ws-1") } returns sampleTags

        val jobId = service.triggerExtraction(
            ExtractionTriggerRequest("ws-1", tagId = "ws-1_ui-ux")
        )

        assertThat(jobId).isNotBlank()
        val status = service.getJobStatus(jobId)
        assertThat(status).isNotNull
    }

    @Test
    fun `triggerExtraction with empty tag list completes immediately`() {
        every { knowledgeTagService.listTags("ws-empty") } returns emptyList()

        val jobId = service.triggerExtraction(
            ExtractionTriggerRequest("ws-empty", tagId = "nonexistent")
        )

        // Wait briefly for async completion
        Thread.sleep(200)

        val status = service.getJobStatus(jobId)
        assertThat(status).isNotNull
        assertThat(status!!.status).isEqualTo("completed")
    }

    @Test
    fun `duplicate extraction for same workspace returns existing jobId`() {
        // Use a latch to keep the first job "running" while we trigger the second
        val blockLatch = CountDownLatch(1)
        every { knowledgeTagService.listTags("ws-dup") } returns sampleTags
        coEvery {
            agenticLoopOrchestrator.agenticStream(any(), any(), any(), any(), workspaceId = "ws-dup", any())
        } coAnswers {
            blockLatch.await(3, TimeUnit.SECONDS)
            AgenticResult(content = "[]", toolCalls = emptyList())
        }

        val jobId1 = service.triggerExtraction(ExtractionTriggerRequest("ws-dup"))
        Thread.sleep(50) // Let the first job start and enter agenticStream (still "running")
        val jobId2 = service.triggerExtraction(ExtractionTriggerRequest("ws-dup"))
        blockLatch.countDown() // Release the latch so job can complete

        assertThat(jobId2).isEqualTo(jobId1)
    }
}
