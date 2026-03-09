package com.forge.webide.service

import com.forge.adapter.model.*
import com.forge.webide.entity.KnowledgeExtractionLogEntity
import com.forge.webide.model.*
import com.forge.webide.repository.KnowledgeExtractionLogRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
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
    private val modelRegistry: ModelRegistry
) {

    private val logger = LoggerFactory.getLogger(KnowledgeExtractionService::class.java)
    private val executor = Executors.newFixedThreadPool(2)
    private val activeJobs = ConcurrentHashMap<String, ExtractionJobStatus>()

    // =========================================================================
    // Public API
    // =========================================================================

    fun triggerExtraction(request: ExtractionTriggerRequest): String {
        // Prevent duplicate jobs for the exact same operation (same workspaceId + same tagId).
        // A single-tag extraction is allowed even when a full-workspace job is running.
        val existingJob = activeJobs.values.find { job ->
            job.status == "running" &&
            job.workspaceId == request.workspaceId &&
            job.tagId == request.tagId
        }
        if (existingJob != null) {
            logger.warn("Identical extraction job already running for workspace {}, tag {}: {}",
                request.workspaceId, request.tagId, existingJob.jobId)
            return existingJob.jobId
        }

        val jobId = UUID.randomUUID().toString().take(8)
        val status = ExtractionJobStatus(
            jobId = jobId,
            status = "running",
            progress = ExtractionProgress(totalTags = 0, completedTags = 0, currentTag = "initializing"),
            results = emptyList(),
            workspaceId = request.workspaceId,
            tagId = request.tagId
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
                val keyFiles = discovery?.keyFiles ?: emptyList()
                val discoveryReason = if (tag.tagKey == "flow-diagrams") discovery?.reason else null
                val content = runTagExtraction(workspaceId, tag, options, adapter, keyFiles, discoveryReason)
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

    internal data class DiscoveryResult(
        val applicable: Boolean,
        val reason: String?,
        val keyFiles: List<String> = emptyList()
    )

    private fun runDiscovery(
        workspaceId: String,
        baseOptions: CompletionOptions,
        @Suppress("UNUSED_PARAMETER") jobId: String,
        tags: List<KnowledgeTag>,
        adapter: ModelAdapter? = null
    ): Map<String, DiscoveryResult> {
        val tagListText = tags.mapIndexed { i, tag ->
            "${i + 1}. ${tag.tagKey ?: tag.id} — ${tag.name}（${tag.chapterHeading}）"
        }.joinToString("\n")

        val systemPrompt = """
你是一位资深软件架构师。分析 workspace 代码库，为下列每个文档领域判断其适用性，并找出最相关的文件清单。

## 待判断的文档领域
$tagListText

## 操作步骤
1. 用 analyze_codebase 获取整体结构（语言、框架、模块数量、文件总数）
2. 用 workspace_list_files 查看根目录 + 关键子目录树
3. 对以下目录逐一检查（如存在）：
   - infrastructure/ | docker/ | deploy/ | k8s/ | terraform/（部署配置）
   - .github/workflows/ | ci/ | .gitlab-ci.yml（CI/CD）
   - src/ | app/ | lib/ | pkg/（源代码）
   - test/ | tests/ | __tests__/ | spec/（测试）
4. 用 workspace_read_file 深入 1-3 个最关键文件来确认技术栈

## 各领域重点扫描模式
- **ui-ux**: src/app/, pages/, templates/, views/, components/, static/
- **api-contract**: *Controller.*, *router.*, *routes.*, *Handler.*, openapi*.yml
- **data-model**: *Entity.*, models.py, schema.prisma, *Repository.*, migrations/, alembic/, *.sql
- **arch-decision**: infrastructure/, docker-compose*.yml, nginx*.conf, k8s/, terraform/, Dockerfile*, settings.gradle.kts, go.mod, requirements.txt
- **verification**: *Test.*, *_test.*, *.test.ts, *.spec.ts, .github/workflows/, pytest.ini, jest.config.*
- **frontend-spec**: package.json, next.config.*, vite.config.*, tailwind.config.*, webpack.config.*
- **change-rules**: .github/workflows/, CONTRIBUTING.md, Makefile, .gitconfig, .husky/, CHANGELOG.md
- **flow-diagrams**:
  扫描所有 *Controller.*、*Service.*（排除 *Test.*、*Config.*、*Mock.*），按"业务域"分组
  识别三类流程价值：
    a) 有 status/state 字段且有多个枚举值 → stateDiagram 候选
    b) Service 方法体 > 20 行，含多步调用链 → flowchart 候选
    c) Controller 调用 2+ 个 Service → sequenceDiagram 候选
  keyFiles 格式：每项 "域名/Controller文件路径" 和 "域名/Service文件路径"
  reason 格式（必须遵守）："发现 N 个业务域｜预期流程: 流程1名称, 流程2名称, ..."（最多 12 个）
  applicable=false 仅当代码库无 Controller/Service 层（纯脚本项目）

## 输出格式（必须是纯 JSON 数组，不含其他文字）
```json
[
  {
    "tagId": "ui-ux",
    "applicable": true,
    "reason": "项目包含 Next.js 15 前端，src/app/ 下有 9 个路由页面",
    "keyFiles": ["web-ide/frontend/src/app/layout.tsx", "web-ide/frontend/src/components/common/Sidebar.tsx", "web-ide/frontend/package.json"]
  }
]
```

规则：
- 每个 tag 必须有一条记录
- keyFiles 列出最能代表该领域的 3-8 个文件路径，供后续析出阶段直接读取
- applicable=false 仅当代码库完全没有该领域的代码/配置时使用
- reason 一句话说明判定依据
        """.trim()

        val messages = listOf(
            Message(role = Message.Role.USER, content = "请分析 workspace 代码库，判断每个标准文档领域是否有内容可写，并列出每个领域最相关的文件。")
        )
        val options = baseOptions.copy(systemPrompt = systemPrompt, maxTokens = 6000)
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
                val keyFilesArray = obj.get("keyFiles")?.asJsonArray
                val keyFiles = keyFilesArray?.map { it.asString } ?: emptyList()
                results[tagId] = DiscoveryResult(applicable, reason, keyFiles)
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
        adapter: ModelAdapter? = null,
        keyFiles: List<String> = emptyList(),
        discoveryReason: String? = null
    ): String {
        val extractionPrompt = getExtractionPrompt(tag.tagKey ?: tag.id, tag.name, keyFiles, discoveryReason)

        val messages = listOf(
            Message(role = Message.Role.USER, content = "请分析 workspace 代码库，生成「${tag.name}」标准文档。")
        )
        // Increase maxTokens for deeper extraction (per-tag phase needs more space than discovery)
        val options = baseOptions.copy(systemPrompt = extractionPrompt, maxTokens = 16384)
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

    private fun getExtractionPrompt(tagId: String, tagName: String, keyFiles: List<String> = emptyList(), discoveryReason: String? = null): String {
        val keyFilesHint = if (keyFiles.isNotEmpty()) {
            """
## 优先读取的关键文件（Discovery 阶段已定位）
以下文件最相关，优先用 workspace_read_file 读取：
${keyFiles.joinToString("\n") { "- $it" }}

在读取这些文件后，再根据需要进一步探索其他文件。
""".trim()
        } else {
            ""
        }

        val baseInstructions = """
你是一位资深软件架构师。分析 workspace 代码库，生成「$tagName」标准文档。

## 通用规则
1. **只写实际存在的内容** — 基于代码/配置，不编造。遇到不确定内容，用 ⚠️ 标注："⚠️ 未在代码中找到，需人工确认"
2. **引用具体文件路径** — 格式："见 `src/controller/UserController.kt:45`"
3. **精确数字** — 用 `grep` 逻辑统计精确数量（如测试方法数），不写 "~20+" 这类模糊描述
4. **输出格式** — 纯 Markdown，章节用 `##`，代码用 code block，表格用 GitHub Markdown 表格
5. **扫描顺序** — 先读 keyFiles（已提供），再根据需要用 workspace_list_files + workspace_read_file 补充
6. **越界内容标注** — 若在扫描中发现明显超出本文档领域的内容（如代码中比文档记录多的实体/端点），用 ⚠️ 标注："⚠️ 以下内容超出预期范围，已从代码发现但需人工验证是否纳入基线"
$keyFilesHint
        """.trim()

        val specificInstructions = when (tagId) {
            "ui-ux" -> """
## 专项要求——UI/UX 设计基线

**第一步：检测前端框架**
- 读取 package.json / requirements.txt / Gemfile / go.mod，确认前端技术栈
- 支持：Next.js / React / Vue / Angular / Django Templates / Jinja2 / Thymeleaf / HTMX / Go html/template

**第二步：扫描路由与页面**
- Next.js/Nuxt：扫描 src/app/、pages/ 目录
- React SPA：扫描 src/routes.tsx 或 src/App.tsx
- Django/Flask：扫描 urls.py、views.py、templates/
- 要求：**列出全部页面路由**（路径 + 用途 + 对应文件），以 Markdown 表格输出，不省略

**第三步：分析组件结构**
- 扫描 src/components/ 或等效目录，统计并列出**所有**组件（含路径）
- 标注组件类型：Layout/Page/Common/Feature
- 描述组件复用模式（如 CVA 变体管理、HOC、Hooks）

**第四步：总结交互模式**
- 导航方式（侧边栏/顶部 Tab/面包屑）
- 表单交互（校验时机、提交反馈）
- 弹窗/Modal/Drawer 使用场景
- 通知/Toast/Snackbar 模式
- 键盘快捷键（如有）

**第五步：分析样式方案**
- 读取 tailwind.config.* / globals.css / styles/ 目录
- 列出颜色变量（包含实际值，如 `--background: 222.2 84% 4.9%`）
- 主题系统（亮/暗模式切换机制）
- 响应式断点和布局规则

**第六步：识别设计问题**
- 列出不一致之处（如 window.prompt 混用、图标尺寸不统一）
- 给出具体改进建议（含受影响的文件）
""".trim()

            "api-contract" -> """
## 专项要求——API 契约基线

**第一步：检测 API 框架**
- Spring Boot/Ktor（Kotlin/Java）：找 @RestController、@Controller
- FastAPI/Flask/Django REST（Python）：找 @app.route、router.include_router、urlpatterns
- Express/NestJS（Node.js）：找 router.get/post、@Controller
- Go（Gin/Echo/Fiber）：找 r.GET、e.POST、v1.Handle
- 用 workspace_list_files 定位所有 Controller/Router/Handler 文件

**第二步：枚举全部 API 端点**
- 逐个读取所有 Controller/Router 文件
- **必须输出精确端点总数**（如"共 47 个 REST 端点"，不写 "~40+"）
- 每个模块用表格列出：HTTP 方法 | 路径 | 参数 | 响应类型 | 说明
- 不能省略任何模块或端点，即使功能相似

**第三步：分析特殊协议**
- WebSocket 端点（握手路径、消息格式）
- SSE/Server-Sent Events 端点（事件类型完整列表）
- 文件上传/下载端点
- GraphQL schema（如有）

**第四步：请求/响应数据结构**
- 核心数据模型（Request/Response DTO）
- JSON Schema 或类型定义
- 分页格式、错误响应格式

**第五步：错误处理与安全**
- HTTP 状态码使用规则（2xx/4xx/5xx 各场景）
- 统一错误响应体格式
- 认证/授权机制（Bearer Token / Session / API Key）
- 安全规则（路径校验、权限检查）

**第六步：API 质量分析**
- 命名不一致之处
- 缺少版本管理的端点
- 改进建议
""".trim()

            "data-model" -> """
## 专项要求——数据模型基线

**第一步：检测 ORM 和数据库**
- JPA/Hibernate（Kotlin/Java）：找 @Entity、@Table 注解，读取 *Entity.kt 文件
- Django ORM（Python）：找 models.py，读取 class * extends models.Model
- SQLAlchemy（Python）：找 Base = declarative_base()，*models.py
- Prisma（TypeScript/Node.js）：读取 prisma/schema.prisma
- GORM（Go）：找 gorm.Model 嵌入，读取 *model.go
- ActiveRecord（Ruby）：找 ApplicationRecord 子类
- 读取 application.yml / .env 确认数据库类型（PostgreSQL/MySQL/SQLite/H2）

**第二步：列出所有实体/表**
- 逐一读取所有模型文件，**精确统计实体总数**
- 每个实体用表格：字段名 | 类型 | 约束 | 说明
- 标注特殊注解（如 `@Column(columnDefinition="CLOB")`、`length=1_000_000`）
- 标注 @UniqueConstraint、@Index
- ⚠️ 若发现比预期更多的实体，单独列出并标注"需人工确认是否纳入基线"

**第三步：分析实体关系**
- 外键关系（OneToMany / ManyToOne / ManyToMany）
- 用 ASCII 树或 Mermaid ERD 描述核心关系
- 级联规则（cascade delete / orphanRemoval）

**第四步：数据库迁移**
- Flyway（.sql 文件）：列出所有迁移版本（V1, V2…），**精确描述每个版本的变更**
- Liquibase（XML/YAML）：列出 changeset ID 和操作
- Django Migrations：列出 migrations/ 目录，描述核心 schema 变更
- Alembic：列出 versions/ 目录
- **必须列出精确迁移版本数量**

**第五步：数据验证规则**
- 字段级约束（非空、长度限制、枚举值）
- 跨字段业务规则（如唯一组合约束）
- 数据库级 CHECK 约束

**第六步：数据模型问题**
- 缺少索引的高频查询字段
- N+1 查询风险
- 数据类型设计问题
""".trim()

            "arch-decision" -> """
## 专项要求——架构决策基线

**⚠️ 重要：部署架构必须单独成章，这是高优先级内容**

**第一步：检测项目类型和规模**
- 读取根目录文件：settings.gradle.kts / pom.xml / go.mod / pyproject.toml / package.json
- 确认架构风格：单体 / 模块化单体 / 微服务 / Serverless

**第二步：【必做】扫描部署架构**
- 用 workspace_list_files 扫描以下路径（逐一检查是否存在）：
  - `infrastructure/` `docker/` `deploy/` `deployment/`
  - `docker-compose.yml` `docker-compose.*.yml`（列出所有变体）
  - `nginx.conf` `nginx/` `*.nginx.conf`
  - `k8s/` `kubernetes/` `helm/` `*.yaml`（Kubernetes 资源）
  - `terraform/` `*.tf`
  - `Dockerfile` `Dockerfile.*`（列出所有变体）
- **必须读取所有 docker-compose*.yml 文件**，提取：
  - 容器清单（名称 + 镜像 + 端口映射）
  - 服务依赖关系（depends_on）
  - 环境变量（非敏感部分）
  - Volume 挂载
  - 健康检查配置
- **必须读取 nginx 配置文件**，提取所有路由规则
- 用 ASCII 图描述完整部署拓扑（容器→端口→网络关系）
- 若上述文件不存在，明确写出 ⚠️ "未找到部署配置文件"

**第三步：技术选型分析**
- 读取构建文件（Gradle/Maven/go.mod/requirements.txt），提取依赖列表
- 核心框架和版本（含确切版本号）
- 关键库选型及其选型理由（从代码注释/ADR/CLAUDE.md 推断）
- 外部依赖（消息队列/缓存/搜索引擎等）

**第四步：模块划分**
- Gradle 多模块 / Maven 多模块 / Go workspaces / Python 包
- 模块职责表（名称 | 职责 | 依赖关系）
- 禁止的循环依赖检查（如有 ArchUnit / 静态检查规则，读取）

**第五步：设计模式识别**
- 代码中实际使用的设计模式（需找到具体文件作为证据）
- 格式：模式名 | 使用位置 | 解决的问题

**第六步：ADR（架构决策记录）**
- 扫描 docs/adr/ / decisions/ / adr/ 目录
- 若使用注释形式的 ADR，扫描代码注释
- 列出每条 ADR 的状态（已实施/计划中/已废弃）

**第七步：架构风险**
- 单点故障风险
- 扩展性瓶颈
- 技术债务
""".trim()

            "verification" -> """
## 专项要求——验证状态

**第一步：检测测试框架**
- Kotlin/Java：JUnit 5、TestNG、MockK、Mockito、Spring Boot Test、AssertJ
- Python：pytest、unittest、coverage、faker
- TypeScript/JS：Jest、Vitest、Playwright、Cypress、Testing Library
- Go：testing 包、testify
- 读取 build.gradle.kts / pom.xml / package.json / requirements.txt 确认版本

**第二步：统计测试数量（精确）**
- 用 workspace_list_files 列出所有 test/ 目录
- **逐个读取测试文件，统计 @Test 方法数量（或 test() / it() 函数数量）**
- 分类统计：单元测试 / 集成测试 / E2E 测试
- 输出：**测试文件数量 = X，测试方法总数 = Y**（必须精确，不写"~"）
- 若数量过多无法逐一计数，读取 5-10 个文件后推算，标注"约"

**第三步：测试覆盖率分析**
- 读取 .jacoco / coverage/ / .nyc_output/ 覆盖率报告（如存在）
- 逐模块描述测试密度（测试文件数 / 源码文件数）
- 识别测试薄弱模块（缺少测试的重要类/函数）

**第四步：CI/CD 配置**
- 读取所有 .github/workflows/*.yml 文件
- 列出 workflow 名称 + 触发条件 + 关键 steps（build/test/deploy）
- 识别是否有以下阶段：lint / type-check / security scan / coverage gate

**第五步：质量门禁**
- 代码规范检查（ESLint / ktlint / flake8 / golangci-lint）
- 类型检查（TypeScript tsc / mypy / pyright）
- 安全扫描（SAST 工具、依赖漏洞扫描）
- 架构约束测试（ArchUnit / 自定义规则）
- 基线脚本（如存在 baselines/*.sh，逐一读取并描述）

**第六步：充分性评估**
- 测试充分性总结（表格：模块 | 覆盖情况 | 评价）
- 具体的测试不足之处（哪些重要功能缺少测试）
- 改进建议（优先级排序）
""".trim()

            "frontend-spec" -> """
## 专项要求——前端设计规范

**第一步：检测前端技术栈（必须读取配置文件）**
- 读取 package.json：列出全部依赖（UI 框架、状态管理、样式、路由、工具链）
- 读取 tsconfig.json / jsconfig.json：TypeScript 配置（strict 模式、路径别名）
- 读取 next.config.* / vite.config.* / webpack.config.*：构建配置、代理配置

**第二步：目录结构规范**
- 用 workspace_list_files 详细扫描 src/、app/、components/ 目录
- 输出完整目录树（至少 3 层深度）
- 说明每个目录的职责和命名约定

**第三步：组件规范**
- 文件命名规范（PascalCase / kebab-case）
- 组件类型（函数式/类组件）
- Props 类型定义（TypeScript interface / type）
- 组件变体管理（CVA / 手动条件类）
- 示例：从代码中提取 1-2 个典型组件的完整定义

**第四步：状态管理方案**
- 分层说明：服务端状态（React Query / SWR / Apollo）| 客户端状态（useState / Zustand / Redux）| 持久化状态（localStorage / sessionStorage / cookie）
- 每层的具体使用模式（含代码示例）
- 状态共享策略

**第五步：API 客户端规范**
- API 请求封装方式（fetch wrapper / axios instance / tRPC）
- 认证 header 注入方式
- 错误处理统一模式（401 自动跳转 / 全局 toast）
- WebSocket / SSE 连接管理
- 提取实际代码作为示例

**第六步：样式约定**
- 读取 tailwind.config.* 和 globals.css，列出：
  - 自定义颜色 token（含实际 HSL/HEX 值）
  - 字体配置
  - 自定义动画/keyframes
  - 自定义 spacing / breakpoint
- 组件样式约定（hover/focus/disabled 状态统一规则）
- 间距约定（gap / padding / margin 常用值）

**第七步：路由结构**
- 路由定义方式（文件系统路由 / 手动配置）
- Layout 嵌套结构
- 动态路由 / 路由参数
- 路由守卫/鉴权检查

**第八步：开发工作流规范**
- 环境变量（列出所有 NEXT_PUBLIC_* / VITE_* 变量）
- 开发服务器启动命令
- 构建命令（区分 dev/build/test）
- 代码格式化工具（Prettier / ESLint 配置）
""".trim()

            "change-rules" -> """
## 专项要求——变更规则

**第一步：扫描所有相关配置文件**
- .github/workflows/：读取所有 workflow YAML 文件（包含名称、触发条件、完整 steps）
- .husky/ 或 git hooks：读取 pre-commit、commit-msg 等 hook 内容
- CONTRIBUTING.md / DEVELOPMENT.md：读取完整文档
- .gitconfig / .git/config：检查 repo 级别配置
- Makefile：列出所有 target（如有）
- CHANGELOG.md：分析变更记录格式（如有）

**第二步：Git 工作流**
- 分支策略（main/develop/feature/* 等）— 从 CI 配置或 CONTRIBUTING.md 推断
- Commit 规范（Conventional Commits / 自定义格式）— 从 commitlint / husky 配置推断
  - **列出所有有效的 type（feat/fix/docs/...）和 scope**
- PR/MR 规则（required reviewers / auto merge 条件）
- Branch protection 规则（从 CI 配置推断）

**第三步：CI/CD 流水线（精确描述）**
- 读取每个 workflow 文件，描述：
  - 触发事件（push/PR/schedule/manual）
  - 执行阶段（列出所有 job 和 step）
  - 环境（OS / 语言版本 / 缓存策略）
  - 部署目标（staging / production）
- 精确列出关键命令（如 `./gradlew :web-ide:backend:test`）

**第四步：代码审查规范**
- 审查维度（从 CONTRIBUTING.md 或规范文档中提取）
- 必须通过的检查（lint / type-check / test / baseline）
- 审查清单（如有明确列出，完整转录）

**第五步：发布流程**
- 版本命名规范（SemVer / CalVer / 自定义）
- 发布前检查清单（从文档或 CI 推断）
- 部署命令（从 CI 或文档提取精确命令）
- 回滚策略

**第六步：配置管理**
- 环境变量管理（.env.example / 配置中心 / Secrets 管理）
- 各环境差异（dev / staging / production 的关键配置差异）
- 敏感信息处理规范

**第七步：规范落地验证**
- **对每条规范，验证其实际代码落地状态**：
  - 规范文档中描述的内容 → 检查对应代码/配置是否存在
  - 若描述与实际不符，标注 ⚠️："规范文档写了 X，但实际代码未找到对应实现"
  - 这是防止"设计意图 vs 实际状态"脱节的关键步骤

**第八步：风险识别**
- 缺少自动化的变更环节
- 规范中描述但未实际落地的流程
- 配置漂移风险
""".trim()

            "flow-diagrams" -> {
                val discoveryReasonBlock = if (!discoveryReason.isNullOrBlank()) {
                    """
**Discovery 阶段预分析结果**（直接使用，无需重新 analyze_codebase）：
$discoveryReason

""".trimIndent()
                } else {
                    "（Discovery 未提供预分析，请先用 analyze_codebase 获取整体结构，再按业务域选择性读取关键文件）\n\n"
                }
                """
## 专项要求——业务流程图

**核心原则**：
- 只基于实际读取的代码生成，不猜测、不编造
- 每个流程图引用真实函数名、状态枚举值、端点路径
- 最多生成 12 个流程图，按业务重要性排序（核心链路优先）
- 每个图独立可理解

**利用 Discovery 预分析（无需重新 analyze_codebase）**：
$discoveryReasonBlock
直接从上方的"预期流程"列表出发，依次为每个流程读取相关文件并生成图。

**读文件策略（节省 context 的关键）**：
- Controller 文件：只读 HTTP 方法签名 + 端点路径 + 调用的 Service 方法名（30-50 行）
- Service 文件：只读 public 方法签名 + when/if 分支和状态转换逻辑（不读私有方法）
- Entity 文件：只读 status/state 字段及其枚举值
- **硬约束**：每个流程图的生成不超过 3 次 workspace_read_file 调用

**图类型选择规则**：
- 有 status 字段 + 多个枚举值 → stateDiagram-v2
- 有跨层/跨服务调用（Controller→Service→外部）→ sequenceDiagram
- 单服务内条件分支流程 → flowchart TD

**输出格式（严格遵守）**：
每个流程图使用如下格式：

## [序号]. [流程名称]（[图类型]）

[1-2 句描述：触发条件 + 关键函数名 + 涉及文件路径]

```mermaid
[图代码]
```

**生成前自检**：节点文字均为真实代码名称？图中无"处理业务逻辑"等模糊描述？Mermaid 语法正确？
""".trim()
            }

            else -> """
## 专项要求

请全面分析与「$tagName」相关的代码、配置和文档，生成结构化的标准文档。
注意：使用精确数字，标注不确定内容，引用具体文件路径。
""".trim()
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
        val allTools = mcpProxyService.listTools()
        val filtered = allTools
            .filter { it.name in allowedTools }
            .map { ToolDefinition(name = it.name, description = it.description, inputSchema = it.inputSchema) }
        logger.info("Extraction tools: allTools={} [{}], filtered={} [{}]",
            allTools.size, allTools.joinToString(", ") { it.name },
            filtered.size, filtered.joinToString(", ") { it.name })
        return filtered
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
