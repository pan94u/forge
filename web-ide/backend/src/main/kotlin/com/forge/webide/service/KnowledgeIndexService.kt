package com.forge.webide.service

import com.forge.webide.entity.KnowledgeDocumentEntity
import com.forge.webide.model.*
import com.forge.webide.repository.KnowledgeDocumentRepository
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Indexes and searches the knowledge base with scope layering
 * (Global / Workspace / Personal).
 *
 * Documents are persisted to DB via KnowledgeDocumentRepository.
 * Sample data (service graph, API catalog, diagrams) remain in-memory.
 *
 * Search priority (when scope=null, cascade mode):
 *   workspace (+20 boost) > personal (+10 boost) > global (+0)
 */
@Service
class KnowledgeIndexService(
    private val knowledgeDocumentRepository: KnowledgeDocumentRepository
) {

    private val logger = LoggerFactory.getLogger(KnowledgeIndexService::class.java)
    private val objectMapper = jacksonObjectMapper()

    private val serviceGraph: ServiceGraph = buildSampleServiceGraph()
    private val apiCatalog: List<ApiService> = buildSampleApiCatalog()
    private val diagrams: List<ArchDiagram> = buildSampleDiagrams()

    init {
        initializeSampleData()
    }

    // =========================================================================
    // Search with scope layering
    // =========================================================================

    /**
     * Search documents by query with scope filtering.
     *
     * @param scope null = cascade (workspace > personal > global), or specific scope
     * @param workspaceId required when scope=WORKSPACE or cascade
     * @param userId required when scope=PERSONAL or cascade
     */
    fun search(
        query: String,
        type: DocumentType?,
        limit: Int = 20,
        scope: KnowledgeScope? = null,
        workspaceId: String? = null,
        userId: String? = null
    ): List<KnowledgeDocument> {
        // 1. Collect candidate documents based on scope
        val candidates = when (scope) {
            KnowledgeScope.GLOBAL -> knowledgeDocumentRepository.findByScopeAndScopeId(KnowledgeScope.GLOBAL, null)
            KnowledgeScope.WORKSPACE -> {
                if (workspaceId != null) {
                    knowledgeDocumentRepository.findByScopeAndScopeId(KnowledgeScope.WORKSPACE, workspaceId)
                } else {
                    emptyList()
                }
            }
            KnowledgeScope.PERSONAL -> {
                if (userId != null) {
                    knowledgeDocumentRepository.findByScopeAndScopeId(KnowledgeScope.PERSONAL, userId)
                } else {
                    emptyList()
                }
            }
            null -> {
                // Cascade: collect all relevant scopes
                val all = mutableListOf<KnowledgeDocumentEntity>()
                all.addAll(knowledgeDocumentRepository.findByScopeAndScopeId(KnowledgeScope.GLOBAL, null))
                if (workspaceId != null) {
                    all.addAll(knowledgeDocumentRepository.findByScopeAndScopeId(KnowledgeScope.WORKSPACE, workspaceId))
                }
                if (userId != null) {
                    all.addAll(knowledgeDocumentRepository.findByScopeAndScopeId(KnowledgeScope.PERSONAL, userId))
                }
                all
            }
        }

        var results = candidates.map { it.toModel() }

        // 2. Filter by type
        if (type != null) {
            results = results.filter { it.type == type }
        }

        // 3. Filter by query + score
        if (query.isNotBlank()) {
            val queryLower = query.lowercase()
            results = results.filter { doc ->
                doc.title.lowercase().contains(queryLower) ||
                doc.content.lowercase().contains(queryLower) ||
                doc.tags.any { it.lowercase().contains(queryLower) } ||
                doc.snippet.lowercase().contains(queryLower)
            }

            // Sort by relevance with scope boost
            results = results.sortedByDescending { doc ->
                var score = 0
                if (doc.title.lowercase().contains(queryLower)) score += 10
                if (doc.tags.any { it.lowercase() == queryLower }) score += 5
                if (doc.snippet.lowercase().contains(queryLower)) score += 3
                // Scope priority boost (cascade mode)
                when (doc.scope) {
                    KnowledgeScope.WORKSPACE -> score += 20
                    KnowledgeScope.PERSONAL -> score += 10
                    KnowledgeScope.GLOBAL -> score += 0
                }
                score
            }
        } else {
            // No query: sort by scope priority then by updatedAt
            results = results.sortedWith(
                compareByDescending<KnowledgeDocument> {
                    when (it.scope) {
                        KnowledgeScope.WORKSPACE -> 2
                        KnowledgeScope.PERSONAL -> 1
                        KnowledgeScope.GLOBAL -> 0
                    }
                }.thenByDescending { it.updatedAt }
            )
        }

        return results.take(limit)
    }

    // =========================================================================
    // CRUD operations
    // =========================================================================

    fun getDocument(id: String): KnowledgeDocument? {
        return knowledgeDocumentRepository.findById(id).orElse(null)?.toModel()
    }

    fun createDocument(request: CreateKnowledgeDocRequest): KnowledgeDocument {
        val entity = KnowledgeDocumentEntity(
            title = request.title,
            type = request.type,
            content = request.content,
            snippet = request.snippet ?: request.content.take(200),
            author = request.author ?: "",
            tags = objectMapper.writeValueAsString(request.tags ?: emptyList<String>()),
            scope = request.scope,
            scopeId = request.scopeId
        )
        knowledgeDocumentRepository.save(entity)
        logger.info("Created knowledge document: {} (scope={}, scopeId={})", entity.title, entity.scope, entity.scopeId)
        return entity.toModel()
    }

    fun updateDocument(id: String, request: UpdateKnowledgeDocRequest): KnowledgeDocument? {
        val entity = knowledgeDocumentRepository.findById(id).orElse(null) ?: return null
        request.title?.let { entity.title = it }
        request.content?.let { entity.content = it }
        request.snippet?.let { entity.snippet = it }
        request.tags?.let { entity.tags = objectMapper.writeValueAsString(it) }
        entity.updatedAt = Instant.now()
        knowledgeDocumentRepository.save(entity)
        logger.info("Updated knowledge document: {} ({})", entity.title, id)
        return entity.toModel()
    }

    fun deleteDocument(id: String): Boolean {
        if (!knowledgeDocumentRepository.existsById(id)) return false
        knowledgeDocumentRepository.deleteById(id)
        logger.info("Deleted knowledge document: {}", id)
        return true
    }

    // =========================================================================
    // Legacy indexing (for compatibility with existing callers)
    // =========================================================================

    fun indexDocument(document: KnowledgeDocument) {
        val entity = KnowledgeDocumentEntity(
            id = document.id,
            title = document.title,
            type = document.type,
            content = document.content,
            snippet = document.snippet,
            author = document.author,
            tags = objectMapper.writeValueAsString(document.tags),
            scope = document.scope,
            scopeId = document.scopeId
        )
        knowledgeDocumentRepository.save(entity)
        logger.info("Indexed document: ${document.title} (${document.id})")
    }

    fun removeDocument(id: String) {
        knowledgeDocumentRepository.deleteById(id)
        logger.info("Removed document from index: $id")
    }

    // =========================================================================
    // Static data (service graph, API catalog, diagrams)
    // =========================================================================

    fun getServiceGraph(): ServiceGraph = serviceGraph
    fun getApiCatalog(): List<ApiService> = apiCatalog
    fun getDiagrams(): List<ArchDiagram> = diagrams

    // =========================================================================
    // Entity ↔ Model mapping
    // =========================================================================

    private fun KnowledgeDocumentEntity.toModel(): KnowledgeDocument {
        val tagsList: List<String> = try {
            objectMapper.readValue(tags)
        } catch (_: Exception) {
            emptyList()
        }
        return KnowledgeDocument(
            id = id,
            title = title,
            type = type,
            content = content,
            snippet = snippet,
            author = author,
            tags = tagsList,
            scope = scope,
            scopeId = scopeId,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    // =========================================================================
    // Sample data initialization (seeded to DB on first run)
    // =========================================================================

    private fun initializeSampleData() {
        // Only seed if DB is empty
        if (knowledgeDocumentRepository.count() > 0) {
            logger.info("Knowledge documents already exist in DB, skipping seed")
            return
        }

        val docs = listOf(
            KnowledgeDocumentEntity(
                id = "doc-1",
                title = "Getting Started with Forge Platform",
                type = DocumentType.WIKI,
                content = """
                    |# Getting Started with Forge Platform
                    |
                    |## Overview
                    |
                    |Forge is an AI-powered development platform that combines a web-based IDE
                    |with knowledge management and workflow automation.
                    |
                    |## Quick Start
                    |
                    |1. Create a new workspace from the dashboard
                    |2. Open the file explorer and start coding
                    |3. Use the AI chat sidebar to ask questions
                    |4. Browse the knowledge base for documentation
                    |
                    |## Architecture
                    |
                    |The platform consists of:
                    |- **Web IDE Frontend**: Next.js 15 with React 19
                    |- **Backend API**: Spring Boot 3 with Kotlin
                    |- **MCP Servers**: Model Context Protocol servers for tool integration
                    |- **Knowledge Base**: Indexed documentation and architecture info
                """.trimMargin(),
                snippet = "Forge is an AI-powered development platform that combines a web-based IDE with knowledge management.",
                author = "Platform Team",
                tags = """["getting-started","platform","overview"]""",
                scope = KnowledgeScope.GLOBAL
            ),
            KnowledgeDocumentEntity(
                id = "doc-2",
                title = "Architecture Overview",
                type = DocumentType.ADR,
                content = """
                    |# ADR-001: Forge Platform Architecture
                    |
                    |## Status
                    |Accepted
                    |
                    |## Context
                    |We need a development platform that integrates AI assistance
                    |with traditional development tools.
                    |
                    |## Decision
                    |We will build Forge as a microservices architecture with:
                    |- Next.js frontend for the web IDE
                    |- Spring Boot backend for API and WebSocket services
                    |- MCP protocol for AI tool integration
                    |- Kubernetes for workspace orchestration
                    |
                    |## Consequences
                    |- Scalable workspace management via K8s pods
                    |- Real-time collaboration through WebSockets
                    |- Extensible AI capabilities through MCP
                """.trimMargin(),
                snippet = "ADR-001: Architecture decision for the Forge platform using microservices.",
                author = "Architecture Team",
                tags = """["architecture","adr","microservices"]""",
                scope = KnowledgeScope.GLOBAL
            ),
            KnowledgeDocumentEntity(
                id = "doc-3",
                title = "API Reference Guide",
                type = DocumentType.API_DOC,
                content = """
                    |# Forge API Reference
                    |
                    |## Base URL
                    |`https://api.forge.dev/v1`
                    |
                    |## Authentication
                    |All API requests require a Bearer token in the Authorization header.
                    |
                    |## Workspaces API
                    |
                    |### Create Workspace
                    |`POST /api/workspaces`
                    |
                    |### List Workspaces
                    |`GET /api/workspaces`
                    |
                    |### Get Workspace
                    |`GET /api/workspaces/{id}`
                    |
                    |## Chat API
                    |
                    |### Create Session
                    |`POST /api/chat/sessions`
                    |
                    |### Stream Chat
                    |`WebSocket /ws/chat/{sessionId}`
                """.trimMargin(),
                snippet = "Complete API reference for the Forge platform REST and WebSocket endpoints.",
                author = "API Team",
                tags = """["api","reference","rest","websocket"]""",
                scope = KnowledgeScope.GLOBAL
            ),
            KnowledgeDocumentEntity(
                id = "doc-4",
                title = "Incident Response Runbook",
                type = DocumentType.RUNBOOK,
                content = """
                    |# Incident Response Runbook
                    |
                    |## Severity Levels
                    |- **P0**: Complete service outage
                    |- **P1**: Major feature degradation
                    |- **P2**: Minor feature issues
                    |- **P3**: Cosmetic issues
                    |
                    |## Steps
                    |
                    |1. Acknowledge the incident in PagerDuty
                    |2. Join the incident Slack channel
                    |3. Assess impact and severity
                    |4. Start investigation using observability tools
                    |5. Communicate status to stakeholders
                    |6. Implement fix or rollback
                    |7. Write post-mortem
                """.trimMargin(),
                snippet = "Standard operating procedures for incident response and management.",
                author = "SRE Team",
                tags = """["runbook","incident","sre","operations"]""",
                scope = KnowledgeScope.GLOBAL
            ),
            KnowledgeDocumentEntity(
                id = "doc-5",
                title = "MCP Server Development Guide",
                type = DocumentType.WIKI,
                content = """
                    |# MCP Server Development Guide
                    |
                    |## What is MCP?
                    |
                    |The Model Context Protocol (MCP) is an open protocol for connecting
                    |AI models to external tools and data sources.
                    |
                    |## Creating a Custom MCP Server
                    |
                    |1. Define your tools with input schemas
                    |2. Implement the tool handlers
                    |3. Register resources for context
                    |4. Deploy and connect to Forge
                    |
                    |## Example Tool Definition
                    |
                    |```kotlin
                    |val tool = McpTool(
                    |    name = "search_docs",
                    |    description = "Search documentation",
                    |    inputSchema = mapOf(
                    |        "type" to "object",
                    |        "properties" to mapOf(
                    |            "query" to mapOf("type" to "string")
                    |        )
                    |    )
                    |)
                    |```
                """.trimMargin(),
                snippet = "Guide for developing custom MCP servers to extend Forge AI capabilities.",
                author = "Platform Team",
                tags = """["mcp","development","guide","tools"]""",
                scope = KnowledgeScope.GLOBAL
            )
        )

        knowledgeDocumentRepository.saveAll(docs)
        logger.info("Seeded ${docs.size} sample knowledge documents to DB")
    }

    private fun buildSampleServiceGraph(): ServiceGraph {
        return ServiceGraph(
            services = listOf(
                ServiceNode("web-ide", "Web IDE", ServiceType.SERVICE, ServiceStatus.HEALTHY, "Platform Team", listOf("api-gateway", "auth-service")),
                ServiceNode("api-gateway", "API Gateway", ServiceType.SERVICE, ServiceStatus.HEALTHY, "Platform Team", listOf("workspace-svc", "chat-svc", "knowledge-svc")),
                ServiceNode("auth-service", "Auth Service", ServiceType.SERVICE, ServiceStatus.HEALTHY, "Security Team", listOf("user-db")),
                ServiceNode("workspace-svc", "Workspace Service", ServiceType.SERVICE, ServiceStatus.HEALTHY, "Platform Team", listOf("workspace-db", "k8s-api")),
                ServiceNode("chat-svc", "Chat Service", ServiceType.SERVICE, ServiceStatus.HEALTHY, "AI Team", listOf("claude-api", "mcp-server", "redis")),
                ServiceNode("knowledge-svc", "Knowledge Service", ServiceType.SERVICE, ServiceStatus.HEALTHY, "Platform Team", listOf("knowledge-db", "search-engine")),
                ServiceNode("mcp-server", "MCP Server", ServiceType.SERVICE, ServiceStatus.HEALTHY, "AI Team", listOf("knowledge-svc")),
                ServiceNode("user-db", "User Database", ServiceType.DATABASE, ServiceStatus.HEALTHY, "DBA Team", emptyList()),
                ServiceNode("workspace-db", "Workspace Database", ServiceType.DATABASE, ServiceStatus.HEALTHY, "DBA Team", emptyList()),
                ServiceNode("knowledge-db", "Knowledge Database", ServiceType.DATABASE, ServiceStatus.HEALTHY, "DBA Team", emptyList()),
                ServiceNode("redis", "Redis Cache", ServiceType.DATABASE, ServiceStatus.HEALTHY, "Platform Team", emptyList()),
                ServiceNode("search-engine", "Search Engine", ServiceType.SERVICE, ServiceStatus.DEGRADED, "Platform Team", emptyList()),
                ServiceNode("k8s-api", "Kubernetes API", ServiceType.EXTERNAL, ServiceStatus.HEALTHY, "Infrastructure Team", emptyList()),
                ServiceNode("claude-api", "Claude API", ServiceType.EXTERNAL, ServiceStatus.HEALTHY, "External", emptyList())
            )
        )
    }

    private fun buildSampleApiCatalog(): List<ApiService> {
        return listOf(
            ApiService(
                id = "workspace-api",
                name = "Workspace Service",
                baseUrl = "http://workspace-svc:8080",
                description = "Manages development workspaces and file operations",
                endpoints = listOf(
                    ApiEndpoint(
                        method = "POST",
                        path = "/api/workspaces",
                        summary = "Create a new workspace",
                        description = "Creates a new development workspace with optional git repository cloning",
                        parameters = emptyList(),
                        requestBody = ApiRequestBody(
                            contentType = "application/json",
                            schema = "CreateWorkspaceRequest",
                            example = """{"name": "my-project", "description": "My new project"}"""
                        ),
                        responses = listOf(
                            ApiResponse(201, "Workspace created", """{"id": "ws-123", "name": "my-project", "status": "creating"}"""),
                            ApiResponse(400, "Invalid request"),
                            ApiResponse(401, "Unauthorized")
                        )
                    ),
                    ApiEndpoint(
                        method = "GET",
                        path = "/api/workspaces",
                        summary = "List workspaces",
                        description = "List all workspaces for the authenticated user",
                        parameters = listOf(
                            ApiParameter("status", "query", false, "string", "Filter by status"),
                            ApiParameter("limit", "query", false, "integer", "Max results")
                        ),
                        responses = listOf(
                            ApiResponse(200, "List of workspaces")
                        )
                    ),
                    ApiEndpoint(
                        method = "GET",
                        path = "/api/workspaces/{id}",
                        summary = "Get workspace details",
                        parameters = listOf(
                            ApiParameter("id", "path", true, "string", "Workspace ID")
                        ),
                        responses = listOf(
                            ApiResponse(200, "Workspace details"),
                            ApiResponse(404, "Workspace not found")
                        )
                    ),
                    ApiEndpoint(
                        method = "DELETE",
                        path = "/api/workspaces/{id}",
                        summary = "Delete a workspace",
                        parameters = listOf(
                            ApiParameter("id", "path", true, "string", "Workspace ID")
                        ),
                        responses = listOf(
                            ApiResponse(204, "Workspace deleted"),
                            ApiResponse(404, "Workspace not found")
                        )
                    )
                )
            ),
            ApiService(
                id = "chat-api",
                name = "Chat Service",
                baseUrl = "http://chat-svc:8080",
                description = "AI chat sessions and message streaming",
                endpoints = listOf(
                    ApiEndpoint(
                        method = "POST",
                        path = "/api/chat/sessions",
                        summary = "Create chat session",
                        requestBody = ApiRequestBody(
                            contentType = "application/json",
                            schema = "CreateChatSessionRequest",
                            example = """{"workspaceId": "ws-123"}"""
                        ),
                        responses = listOf(
                            ApiResponse(201, "Session created", """{"id": "sess-456", "workspaceId": "ws-123"}""")
                        )
                    ),
                    ApiEndpoint(
                        method = "GET",
                        path = "/api/chat/sessions/{id}/messages",
                        summary = "Get chat messages",
                        parameters = listOf(
                            ApiParameter("id", "path", true, "string", "Session ID")
                        ),
                        responses = listOf(
                            ApiResponse(200, "List of messages")
                        )
                    )
                )
            )
        )
    }

    private fun buildSampleDiagrams(): List<ArchDiagram> {
        return listOf(
            ArchDiagram(
                id = "diag-1",
                title = "System Architecture",
                description = "High-level system architecture of the Forge platform",
                mermaidCode = """
                    |graph TB
                    |    User[User Browser] --> FE[Web IDE Frontend]
                    |    FE --> GW[API Gateway]
                    |    GW --> WS[Workspace Service]
                    |    GW --> CS[Chat Service]
                    |    GW --> KS[Knowledge Service]
                    |    CS --> Claude[Claude API]
                    |    CS --> MCP[MCP Servers]
                    |    WS --> K8s[Kubernetes]
                    |    WS --> DB1[(Workspace DB)]
                    |    CS --> Redis[(Redis)]
                    |    KS --> DB2[(Knowledge DB)]
                    |    KS --> SE[Search Engine]
                """.trimMargin()
            ),
            ArchDiagram(
                id = "diag-2",
                title = "Chat Flow Sequence",
                description = "Sequence diagram for AI chat message flow",
                mermaidCode = """
                    |sequenceDiagram
                    |    participant U as User
                    |    participant FE as Frontend
                    |    participant BE as Backend
                    |    participant C as Claude
                    |    participant MCP as MCP Server
                    |    U->>FE: Send message
                    |    FE->>BE: WebSocket message
                    |    BE->>C: API request with tools
                    |    C->>BE: Tool use request
                    |    BE->>MCP: Call tool
                    |    MCP->>BE: Tool result
                    |    BE->>C: Tool result
                    |    C->>BE: Final response
                    |    BE->>FE: Stream response
                    |    FE->>U: Display message
                """.trimMargin()
            ),
            ArchDiagram(
                id = "diag-3",
                title = "Workspace Lifecycle",
                description = "State diagram for workspace lifecycle management",
                mermaidCode = """
                    |stateDiagram-v2
                    |    [*] --> Creating
                    |    Creating --> Active : Pod ready
                    |    Creating --> Error : Pod failed
                    |    Active --> Suspended : User suspends
                    |    Active --> Active : User working
                    |    Suspended --> Active : User activates
                    |    Active --> [*] : User deletes
                    |    Suspended --> [*] : User deletes
                    |    Error --> [*] : User deletes
                    |    Error --> Creating : Retry
                """.trimMargin()
            )
        )
    }
}
