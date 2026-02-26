package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.entity.KnowledgeExtractionLogEntity
import com.forge.webide.model.*
import com.forge.webide.repository.KnowledgeExtractionLogRepository
import com.forge.webide.repository.WorkspaceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlinx.coroutines.runBlocking

@Service
class KnowledgeExtractionService(
    private val agenticLoopOrchestrator: AgenticLoopOrchestrator,
    private val mcpProxyService: McpProxyService,
    private val knowledgeTagService: KnowledgeTagService,
    private val extractionLogRepository: KnowledgeExtractionLogRepository,
    private val claudeAdapter: ModelAdapter,
    private val modelRegistry: ModelRegistry,
    private val workspaceRepository: WorkspaceRepository
) {

    private val logger = LoggerFactory.getLogger(KnowledgeExtractionService::class.java)
    private val executor = Executors.newFixedThreadPool(2)
    private val activeJobs = ConcurrentHashMap<String, ExtractionJobStatus>()

    // =========================================================================
    // Public API
    // =========================================================================

    fun triggerExtraction(request: ExtractionTriggerRequest): String {
        // Prevent duplicate jobs on same workspace
        val existingJob = activeJobs.values.find {
            it.status == "running" && it.workspaceId == request.workspaceId
        }
        if (existingJob != null) {
            logger.warn("Extraction job already running for workspace {}: {}", request.workspaceId, existingJob.jobId)
            return existingJob.jobId
        }

        val jobId = UUID.randomUUID().toString().take(8)
        val status = ExtractionJobStatus(
            jobId = jobId,
            status = "running",
            progress = ExtractionProgress(totalTags = 0, completedTags = 0, currentTag = "initializing"),
            results = emptyList(),
            workspaceId = request.workspaceId
        )
        activeJobs[jobId] = status

        executor.submit {
            try {
                executeExtraction(jobId, request)
            } catch (e: Exception) {
                logger.error("Extraction job failed: jobId=$jobId", e)
                activeJobs[jobId] = activeJobs[jobId]!!.copy(status = "failed")
            }
        }

        logger.info("Triggered extraction job: jobId=$jobId, workspace=${request.workspaceId}, tagId=${request.tagId}")
        return jobId
    }

    fun getJobStatus(jobId: String): ExtractionJobStatus? {
        return activeJobs[jobId]
    }

    fun getLogs(tagId: String?, limit: Int?, workspaceId: String? = null): List<KnowledgeExtractionLog> {
        val entities = when {
            !tagId.isNullOrBlank() -> extractionLogRepository.findByTagIdOrderByCreatedAtDesc(tagId)
            !workspaceId.isNullOrBlank() -> extractionLogRepository.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
            else -> extractionLogRepository.findTop30ByOrderByCreatedAtDesc()
        }
        return entities.map { it.toModel() }
    }

    // =========================================================================
    // Scheduled batch extraction
    // =========================================================================

    @Value("\${forge.knowledge.extraction-enabled:false}")
    private var extractionEnabled: Boolean = false

    @Scheduled(cron = "\${forge.knowledge.extraction-cron:0 0 */4 * * *}")
    fun scheduledBatchExtraction() {
        if (!extractionEnabled) {
            logger.debug("Scheduled extraction disabled (forge.knowledge.extraction-enabled=false)")
            return
        }
        logger.info("Starting scheduled batch knowledge extraction")
        val cutoff = Instant.now().minus(4, ChronoUnit.HOURS)

        val activeWorkspaces = workspaceRepository.findByStatusNot(WorkspaceStatus.SUSPENDED)
            .filter { it.status == WorkspaceStatus.ACTIVE }

        for (workspace in activeWorkspaces) {
            val recentLogs = extractionLogRepository.findByWorkspaceIdAndCreatedAtAfter(workspace.id, cutoff)
            if (recentLogs.isNotEmpty()) {
                logger.debug("Skipping workspace {} — recently refreshed", workspace.id)
                continue
            }

            try {
                logger.info("Batch extraction for workspace: {} ({})", workspace.name, workspace.id)
                val request = ExtractionTriggerRequest(workspaceId = workspace.id)
                triggerExtraction(request)
                // Wait for completion before next workspace to avoid API rate limits
                Thread.sleep(5000)
            } catch (e: Exception) {
                logger.error("Batch extraction failed for workspace {}: {}", workspace.id, e.message)
            }
        }

        logger.info("Scheduled batch extraction completed for {} workspaces", activeWorkspaces.size)
    }

    // =========================================================================
    // Core extraction logic
    // =========================================================================

    private fun executeExtraction(jobId: String, request: ExtractionTriggerRequest) {
        val workspaceId = request.workspaceId
        val modelId = request.modelId
        val adapter = if (modelId != null) modelRegistry.adapterForModel(modelId) else claudeAdapter
        val options = CompletionOptions(
            model = modelId,
            maxTokens = 8192,
            temperature = 0.3,
            systemPrompt = null,
            apiKeyOverride = request.apiKey
        )

        // Determine which tags to process — workspace-scoped!
        val allTags = knowledgeTagService.listTags(request.workspaceId)
        val targetTags = if (!request.tagId.isNullOrBlank()) {
            allTags.filter { it.id == request.tagId }
        } else {
            allTags
        }

        if (targetTags.isEmpty()) {
            activeJobs[jobId] = activeJobs[jobId]!!.copy(status = "completed")
            return
        }

        // Phase 1: Discovery — determine which tags are applicable
        updateJobProgress(jobId, 0, targetTags.size, "discovery")
        val discoveryResults = runDiscovery(workspaceId, options, jobId, targetTags, adapter)

        // Phase 2: Per-tag extraction
        val results = mutableListOf<TagExtractionResult>()
        var completedCount = 0

        for (tag in targetTags) {
            val discovery = discoveryResults[tag.tagKey ?: tag.id]
            val applicable = discovery?.applicable ?: true
            val reason = discovery?.reason

            if (!applicable) {
                // Mark as not_applicable
                knowledgeTagService.updateTag(tag.id, UpdateKnowledgeTagRequest(
                    status = "not_applicable",
                    description = reason ?: "AI determined no relevant code found"
                ))
                saveLog(jobId, workspaceId, tag, "extraction", "skipped",
                    applicable = false, reason = reason, durationMs = 0,
                    modelUsed = modelId)

                results.add(TagExtractionResult(
                    tagId = tag.id, tagName = tag.name,
                    applicable = false, reason = reason, contentLength = 0
                ))
                completedCount++
                updateJobProgress(jobId, completedCount, targetTags.size, null, results)
                continue
            }

            // Mark as extracting
            knowledgeTagService.updateTag(tag.id, UpdateKnowledgeTagRequest(status = "extracting"))
            updateJobProgress(jobId, completedCount, targetTags.size, tag.name, results)

            val startMs = System.currentTimeMillis()
            try {
                val content = runTagExtraction(workspaceId, tag, options, adapter)
                val durationMs = System.currentTimeMillis() - startMs

                // Update tag with generated content
                knowledgeTagService.updateTag(tag.id, UpdateKnowledgeTagRequest(
                    content = content,
                    status = "draft"
                ))

                saveLog(jobId, workspaceId, tag, "extraction", "extracted",
                    applicable = true, contentLength = content.length,
                    durationMs = durationMs, modelUsed = modelId)

                results.add(TagExtractionResult(
                    tagId = tag.id, tagName = tag.name,
                    applicable = true, reason = null, contentLength = content.length
                ))
            } catch (e: Exception) {
                val durationMs = System.currentTimeMillis() - startMs
                logger.error("Extraction failed for tag ${tag.id}: ${e.message}", e)

                // Revert status to empty on failure
                knowledgeTagService.updateTag(tag.id, UpdateKnowledgeTagRequest(status = "empty"))

                saveLog(jobId, workspaceId, tag, "extraction", "failed",
                    applicable = true, reason = e.message, durationMs = durationMs,
                    modelUsed = modelId)

                results.add(TagExtractionResult(
                    tagId = tag.id, tagName = tag.name,
                    applicable = true, reason = "Extraction failed: ${e.message}",
                    contentLength = 0
                ))
            }
            completedCount++
            updateJobProgress(jobId, completedCount, targetTags.size, null, results)
        }

        activeJobs[jobId] = activeJobs[jobId]!!.copy(
            status = "completed",
            progress = ExtractionProgress(targetTags.size, targetTags.size, null),
            results = results
        )
        logger.info("Extraction job completed: jobId=$jobId, tags=${targetTags.size}, results=${results.size}")
    }

    // =========================================================================
    // Discovery Phase
    // =========================================================================

    internal data class DiscoveryResult(val applicable: Boolean, val reason: String?)

    private fun runDiscovery(
        workspaceId: String,
        baseOptions: CompletionOptions,
        jobId: String,
        tags: List<KnowledgeTag>,
        adapter: ModelAdapter? = null
    ): Map<String, DiscoveryResult> {
        val tagListText = tags.mapIndexed { i, tag ->
            "${i + 1}. ${tag.tagKey ?: tag.id} — ${tag.name}（${tag.chapterHeading}）"
        }.joinToString("\n")

        val systemPrompt = """
你是一位资深软件架构师。分析以下 workspace 的代码库，判断它是否包含以下标准文档领域的内容。

对于每个领域，判断代码库是否有足够的代码/配置/设计来生成有意义的文档。

标准文档领域：
$tagListText

请按以下步骤操作：
1. 先用 analyze_codebase 了解整体结构
2. 用 workspace_list_files 查看文件树
3. 必要时用 workspace_read_file 深入关键文件

最后，你必须输出以下格式的 JSON 数组（不要包含其他内容）：
```json
[{"tagId": "ui-ux", "applicable": true, "reason": "项目包含 React 前端组件和路由配置"}, ...]
```

每个 tag 都必须有一条记录。applicable 为 true 表示可以生成有意义的文档，false 表示代码库不涉及该领域。
reason 用一句话解释判定理由。
        """.trim()

        val messages = listOf(
            Message(role = Message.Role.USER, content = "请分析 workspace 代码库，判断每个标准文档领域是否有内容可写。")
        )
        val options = baseOptions.copy(systemPrompt = systemPrompt, maxTokens = 4096)
        val tools = getExtractionTools()

        val result = runBlocking {
            agenticLoopOrchestrator.agenticStream(
                messages = messages,
                options = options,
                tools = tools,
                onEvent = { /* silent, no WebSocket */ },
                workspaceId = workspaceId,
                adapter = adapter
            )
        }

        // Parse JSON from AI output
        return parseDiscoveryJson(result.content, tags)
    }

    internal fun parseDiscoveryJson(content: String, tags: List<KnowledgeTag>): Map<String, DiscoveryResult> {
        val results = mutableMapOf<String, DiscoveryResult>()

        try {
            // Extract JSON array from content (may be wrapped in ```json ... ```)
            val jsonStr = extractJsonArray(content)
            val gson = com.google.gson.Gson()
            val jsonArray = gson.fromJson(jsonStr, com.google.gson.JsonArray::class.java)

            for (element in jsonArray) {
                val obj = element.asJsonObject
                val tagId = obj.get("tagId")?.asString ?: continue
                val applicable = obj.get("applicable")?.asBoolean ?: true
                val reason = obj.get("reason")?.asString
                results[tagId] = DiscoveryResult(applicable, reason)
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse discovery JSON, defaulting all tags to applicable: ${e.message}")
        }

        // Default: any tags not in results are assumed applicable (use tagKey for matching)
        for (tag in tags) {
            val key = tag.tagKey ?: tag.id
            if (key !in results) {
                results[key] = DiscoveryResult(true, "Default: assumed applicable")
            }
        }

        return results
    }

    private fun extractJsonArray(content: String): String {
        // Try to find JSON within ```json ... ``` blocks
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?(\\[.*?])\\s*```", RegexOption.DOT_MATCHES_ALL)
        val match = codeBlockPattern.find(content)
        if (match != null) return match.groupValues[1]

        // Try to find raw JSON array
        val arrayPattern = Regex("\\[\\s*\\{.*}\\s*]", RegexOption.DOT_MATCHES_ALL)
        val arrayMatch = arrayPattern.find(content)
        if (arrayMatch != null) return arrayMatch.value

        return content
    }

    // =========================================================================
    // Per-tag Extraction Phase
    // =========================================================================

    private fun runTagExtraction(
        workspaceId: String,
        tag: KnowledgeTag,
        baseOptions: CompletionOptions,
        adapter: ModelAdapter? = null
    ): String {
        val extractionPrompt = getExtractionPrompt(tag.tagKey ?: tag.id, tag.name)

        val messages = listOf(
            Message(role = Message.Role.USER, content = "请分析 workspace 代码库，生成「${tag.name}」标准文档。")
        )
        val options = baseOptions.copy(systemPrompt = extractionPrompt, maxTokens = 8192)
        val tools = getExtractionTools()

        val result = runBlocking {
            agenticLoopOrchestrator.agenticStream(
                messages = messages,
                options = options,
                tools = tools,
                onEvent = { /* silent */ },
                workspaceId = workspaceId,
                adapter = adapter
            )
        }

        return result.content
    }

    // =========================================================================
    // Extraction prompt templates
    // =========================================================================

    private fun getExtractionPrompt(tagId: String, tagName: String): String {
        val baseInstructions = """
你是一位资深软件架构师。分析 workspace 代码库，生成「$tagName」标准文档。

通用要求：
1. 内容必须基于实际代码，不要编造不存在的内容
2. 引用具体文件路径（如 "见 src/controller/UserController.kt:45"）
3. 输出格式：纯 Markdown，标题用 ##，代码示例用 code block
4. 先用 workspace_list_files 定位相关文件，再用 workspace_read_file 逐个分析关键文件
5. 如有必要，用 analyze_codebase 获取整体结构信息
        """.trim()

        val specificInstructions = when (tagId) {
            "ui-ux" -> """
专项要求——UI/UX 设计基线：
1. 列出所有前端页面/路由及其用途
2. 分析 UI 组件结构（组件树、复用模式）
3. 总结交互模式（导航、表单、弹窗、通知）
4. 分析样式方案（CSS 框架、主题、响应式）
5. 找出 UI 一致性问题或改进建议
"""
            "api-contract" -> """
专项要求——API 契约基线：
1. 列出所有 REST API endpoints（路径、方法、参数、响应格式）
2. 分析请求/响应 model 的数据结构
3. 总结错误处理模式和状态码使用
4. 分析 API 版本管理和兼容性策略
5. 找出 API 设计不一致或改进建议
"""
            "data-model" -> """
专项要求——数据模型基线：
1. 列出所有数据库表/实体及其字段
2. 分析表之间的关系（外键、索引）
3. 总结数据迁移策略（Flyway/Liquibase）
4. 分析数据验证规则和约束
5. 找出数据模型设计问题或改进建议
"""
            "arch-decision" -> """
专项要求——架构决策基线：
1. 分析整体架构风格（单体/微服务/模块化）
2. 总结技术选型及其理由
3. 分析模块划分和依赖关系
4. 总结设计模式使用（工厂、策略、观察者等）
5. 识别架构优势和潜在风险
"""
            "verification" -> """
专项要求——验证状态：
1. 分析测试覆盖情况（单元测试、集成测试、E2E）
2. 列出测试框架和工具
3. 总结 CI/CD 配置
4. 分析质量门禁（lint、type check、安全扫描）
5. 评估当前测试充分性，给出改进建议
"""
            "frontend-spec" -> """
专项要求——前端设计规范：
1. 分析组件命名规范和文件组织
2. 总结状态管理方案（Redux/Context/Zustand 等）
3. 分析样式约定（CSS Modules/Tailwind/styled-components）
4. 总结路由组织和页面结构
5. 分析代码规范和最佳实践
"""
            "change-rules" -> """
专项要求——变更规则：
1. 分析 Git 工作流（分支策略、commit 规范）
2. 总结代码审查流程
3. 分析发布流程和版本管理
4. 总结配置管理和环境管理
5. 识别变更管理的风险和改进点
"""
            else -> """
请全面分析与「$tagName」相关的代码和配置，生成结构化的标准文档。
"""
        }

        return "$baseInstructions\n\n$specificInstructions"
    }

    // =========================================================================
    // Tool filtering (read-only tools only)
    // =========================================================================

    private fun getExtractionTools(): List<ToolDefinition> {
        val allowedTools = setOf(
            "workspace_list_files", "workspace_read_file", "analyze_codebase",
            "search_knowledge", "read_file"
        )
        return mcpProxyService.listTools()
            .filter { it.name in allowedTools }
            .map { ToolDefinition(name = it.name, description = it.description, inputSchema = it.inputSchema) }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun updateJobProgress(
        jobId: String,
        completed: Int,
        total: Int,
        currentTag: String?,
        results: List<TagExtractionResult> = emptyList()
    ) {
        activeJobs[jobId] = activeJobs[jobId]!!.copy(
            progress = ExtractionProgress(total, completed, currentTag),
            results = results
        )
    }

    private fun saveLog(
        jobId: String,
        workspaceId: String,
        tag: KnowledgeTag,
        phase: String,
        status: String,
        applicable: Boolean = true,
        reason: String? = null,
        contentLength: Int = 0,
        tokensUsed: Int = 0,
        durationMs: Long = 0,
        modelUsed: String? = null,
        sourceFiles: String? = null
    ) {
        val entity = KnowledgeExtractionLogEntity(
            id = UUID.randomUUID().toString().take(12),
            jobId = jobId,
            workspaceId = workspaceId,
            tagId = tag.id,
            tagName = tag.name,
            phase = phase,
            status = status,
            applicable = applicable,
            reason = reason,
            contentLength = contentLength,
            tokensUsed = tokensUsed,
            durationMs = durationMs,
            modelUsed = modelUsed,
            sourceFiles = sourceFiles
        )
        extractionLogRepository.save(entity)
    }

    private fun KnowledgeExtractionLogEntity.toModel(): KnowledgeExtractionLog {
        return KnowledgeExtractionLog(
            id = id,
            jobId = jobId,
            workspaceId = workspaceId,
            tagId = tagId,
            tagName = tagName,
            phase = phase,
            status = status,
            applicable = applicable,
            reason = reason,
            contentLength = contentLength,
            tokensUsed = tokensUsed,
            durationMs = durationMs,
            modelUsed = modelUsed,
            sourceFiles = sourceFiles,
            createdAt = createdAt
        )
    }
}
