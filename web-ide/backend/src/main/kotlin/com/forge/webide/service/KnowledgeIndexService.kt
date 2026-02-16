package com.forge.webide.service

import com.forge.webide.model.*
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Indexes and searches the knowledge base, including documentation,
 * service graphs, API catalogs, and architecture diagrams.
 *
 * In production, this would integrate with a vector database or search
 * engine (e.g., Elasticsearch, Qdrant) for semantic search capabilities.
 */
@Service
class KnowledgeIndexService {

    private val logger = LoggerFactory.getLogger(KnowledgeIndexService::class.java)

    private val documents = ConcurrentHashMap<String, KnowledgeDocument>()
    private val serviceGraph: ServiceGraph
    private val apiCatalog: List<ApiService>
    private val diagrams: List<ArchDiagram>

    init {
        initializeSampleData()
        serviceGraph = buildSampleServiceGraph()
        apiCatalog = buildSampleApiCatalog()
        diagrams = buildSampleDiagrams()
    }

    /**
     * Search documents by query and optional type filter.
     */
    fun search(query: String, type: DocumentType?, limit: Int = 20): List<KnowledgeDocument> {
        var results = documents.values.toList()

        // Filter by type
        if (type != null) {
            results = results.filter { it.type == type }
        }

        // Filter by query (simple text matching; in production use semantic search)
        if (query.isNotBlank()) {
            val queryLower = query.lowercase()
            results = results.filter { doc ->
                doc.title.lowercase().contains(queryLower) ||
                doc.content.lowercase().contains(queryLower) ||
                doc.tags.any { it.lowercase().contains(queryLower) } ||
                doc.snippet.lowercase().contains(queryLower)
            }

            // Sort by relevance (title match first, then content match)
            results = results.sortedByDescending { doc ->
                var score = 0
                if (doc.title.lowercase().contains(queryLower)) score += 10
                if (doc.tags.any { it.lowercase() == queryLower }) score += 5
                if (doc.snippet.lowercase().contains(queryLower)) score += 3
                score
            }
        } else {
            results = results.sortedByDescending { it.updatedAt }
        }

        return results.take(limit)
    }

    /**
     * Get a document by ID.
     */
    fun getDocument(id: String): KnowledgeDocument? {
        return documents[id]
    }

    /**
     * Get the service dependency graph.
     */
    fun getServiceGraph(): ServiceGraph {
        return serviceGraph
    }

    /**
     * Get the API catalog.
     */
    fun getApiCatalog(): List<ApiService> {
        return apiCatalog
    }

    /**
     * Get architecture diagrams.
     */
    fun getDiagrams(): List<ArchDiagram> {
        return diagrams
    }

    /**
     * Index a new document.
     */
    fun indexDocument(document: KnowledgeDocument) {
        documents[document.id] = document
        logger.info("Indexed document: ${document.title} (${document.id})")
    }

    /**
     * Remove a document from the index.
     */
    fun removeDocument(id: String) {
        documents.remove(id)
        logger.info("Removed document from index: $id")
    }

    private fun initializeSampleData() {
        val docs = listOf(
            KnowledgeDocument(
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
                tags = listOf("getting-started", "platform", "overview"),
                relatedDocs = listOf(
                    RelatedDoc("doc-2", "Architecture Overview"),
                    RelatedDoc("doc-3", "API Reference Guide")
                )
            ),
            KnowledgeDocument(
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
                tags = listOf("architecture", "adr", "microservices")
            ),
            KnowledgeDocument(
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
                tags = listOf("api", "reference", "rest", "websocket")
            ),
            KnowledgeDocument(
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
                tags = listOf("runbook", "incident", "sre", "operations")
            ),
            KnowledgeDocument(
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
                tags = listOf("mcp", "development", "guide", "tools")
            )
        )

        docs.forEach { doc ->
            documents[doc.id] = doc
        }

        logger.info("Initialized ${docs.size} sample knowledge documents")
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
                            ApiResponse(201, "Workspace created", """{"id": "ws-123", "name": "my-project", "status": "active"}"""),
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
