package com.forge.webide.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.time.Instant
import java.util.UUID

// --- Workspace Models ---

data class Workspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val status: WorkspaceStatus = WorkspaceStatus.CREATING,
    val owner: String = "",
    val repository: String? = null,
    val branch: String? = null,
    val errorMessage: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class WorkspaceStatus {
    CREATING, ACTIVE, SUSPENDED, ERROR;

    @JsonValue
    fun toValue(): String = name.lowercase()
}

data class CreateWorkspaceRequest(
    val name: String,
    val description: String? = null,
    val repository: String? = null,
    val branch: String? = null,
    val template: String? = null
)

data class FileNode(
    val name: String,
    val path: String,
    val type: FileType,
    val size: Long? = null,
    val children: List<FileNode>? = null
)

enum class FileType {
    FILE, DIRECTORY;

    @JsonValue
    fun toValue(): String = name.lowercase()
}

data class FileContentRequest(
    val path: String,
    val content: String
)

// --- Runtime Service Models ---

data class ServiceInfo(
    val port: Int,
    val command: String,
    val status: String,
    val startTime: String,
    val proxyUrl: String? = null
)

// --- Chat Models ---

data class ChatSession(
    val id: String = UUID.randomUUID().toString(),
    val workspaceId: String,
    val userId: String = "",
    val createdAt: Instant = Instant.now()
)

data class CreateChatSessionRequest(
    val workspaceId: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val timestamp: Instant = Instant.now(),
    val contexts: List<ContextReference>? = null,
    val toolCalls: List<ToolCallRecord>? = null
)

enum class MessageRole {
    USER, ASSISTANT
}

data class ContextReference(
    val type: String,
    val id: String,
    val content: String? = null
)

data class ToolCallRecord(
    val id: String,
    val name: String,
    val input: Map<String, Any?>,
    val output: String? = null,
    val status: String = "complete"
)

data class ChatStreamMessage(
    val type: String,
    val content: String,
    val contexts: List<ContextReference>? = null
)

// --- Knowledge Models ---

data class KnowledgeDocument(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: DocumentType,
    val content: String,
    val snippet: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val scope: KnowledgeScope = KnowledgeScope.GLOBAL,
    val scopeId: String? = null,
    val relatedDocs: List<RelatedDoc> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

enum class DocumentType {
    WIKI, ADR, RUNBOOK, API_DOC, PATTERN, STUB;

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): DocumentType =
            valueOf(value.uppercase().replace("-", "_"))
    }
}

enum class KnowledgeScope {
    GLOBAL, WORKSPACE, PERSONAL;

    @JsonValue
    fun toValue(): String = name.lowercase()

    companion object {
        @JvmStatic
        @JsonCreator
        fun fromValue(value: String): KnowledgeScope =
            valueOf(value.uppercase())
    }
}

data class CreateKnowledgeDocRequest(
    val title: String,
    val type: DocumentType,
    val content: String,
    val snippet: String? = null,
    val author: String? = null,
    val tags: List<String>? = null,
    val scope: KnowledgeScope = KnowledgeScope.GLOBAL,
    val scopeId: String? = null
)

data class UpdateKnowledgeDocRequest(
    val title: String? = null,
    val content: String? = null,
    val snippet: String? = null,
    val tags: List<String>? = null
)

data class RelatedDoc(
    val id: String,
    val title: String
)

data class ServiceNode(
    val id: String,
    val name: String,
    val type: ServiceType,
    val status: ServiceStatus,
    val team: String,
    val dependencies: List<String>
)

enum class ServiceType {
    SERVICE, DATABASE, QUEUE, EXTERNAL;
    @JsonValue fun toValue() = name.lowercase()
}

enum class ServiceStatus {
    HEALTHY, DEGRADED, DOWN;
    @JsonValue fun toValue() = name.lowercase()
}

data class ServiceGraph(
    val services: List<ServiceNode>
)

data class ApiService(
    val id: String,
    val name: String,
    val baseUrl: String,
    val description: String,
    val endpoints: List<ApiEndpoint>
)

data class ApiEndpoint(
    val method: String,
    val path: String,
    val summary: String,
    val description: String = "",
    val parameters: List<ApiParameter> = emptyList(),
    val requestBody: ApiRequestBody? = null,
    val responses: List<ApiResponse> = emptyList()
)

data class ApiParameter(
    val name: String,
    val `in`: String,
    val required: Boolean = false,
    val type: String = "string",
    val description: String = ""
)

data class ApiRequestBody(
    val contentType: String = "application/json",
    val schema: String = "",
    val example: String = ""
)

data class ApiResponse(
    val status: Int,
    val description: String,
    val example: String? = null
)

data class ApiTryRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class ArchDiagram(
    val id: String,
    val title: String,
    val description: String,
    val mermaidCode: String,
    val updatedAt: Instant = Instant.now()
)

// --- Workflow Models ---

data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val nodes: List<WorkflowNodeDef> = emptyList(),
    val edges: List<WorkflowEdgeDef> = emptyList(),
    val owner: String = "",
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class WorkflowNodeDef(
    val id: String,
    val type: String,
    val position: Position,
    val data: Map<String, Any?> = emptyMap()
)

data class Position(
    val x: Double,
    val y: Double
)

data class WorkflowEdgeDef(
    val id: String,
    val source: String,
    val target: String,
    val label: String? = null
)

data class CreateWorkflowRequest(
    val name: String,
    val description: String? = null,
    val nodes: List<WorkflowNodeDef>? = null,
    val edges: List<WorkflowEdgeDef>? = null
)

data class UpdateWorkflowRequest(
    val name: String? = null,
    val description: String? = null,
    val nodes: List<WorkflowNodeDef>? = null,
    val edges: List<WorkflowEdgeDef>? = null
)

data class WorkflowExecutionResult(
    val workflowId: String,
    val status: String,
    val steps: List<StepResult> = emptyList(),
    val startedAt: Instant = Instant.now(),
    val completedAt: Instant? = null
)

data class StepResult(
    val nodeId: String,
    val status: String,
    val output: String? = null,
    val error: String? = null,
    val duration: Long? = null
)

// --- MCP Models ---

data class McpTool(
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?> = emptyMap()
)

data class McpToolCallRequest(
    val name: String,
    val arguments: Map<String, Any?> = emptyMap()
)

data class McpToolCallResponse(
    val content: List<McpContent>,
    val isError: Boolean = false
)

data class McpContent(
    val type: String,
    val text: String? = null,
    val data: String? = null,
    val mimeType: String? = null,
    val uri: String? = null
)

// --- Activity Models ---

data class ActivityItem(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val message: String,
    val timestamp: Instant = Instant.now(),
    val workspace: String? = null
)

// --- Knowledge Gap ---

data class KnowledgeGap(
    val id: String = UUID.randomUUID().toString(),
    val query: String,
    val context: String,
    val detectedAt: Instant = Instant.now(),
    val resolved: Boolean = false,
    val resolvedAt: Instant? = null
)
