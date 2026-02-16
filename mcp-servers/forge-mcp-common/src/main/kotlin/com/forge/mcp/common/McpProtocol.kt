package com.forge.mcp.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * MCP protocol data classes following the Model Context Protocol specification.
 */

@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JsonObject
)

@Serializable
data class ToolCallRequest(
    val name: String,
    val arguments: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class ToolCallResponse(
    val content: List<ToolContent>,
    @SerialName("isError")
    val isError: Boolean = false
)

@Serializable
sealed class ToolContent {
    @Serializable
    @SerialName("text")
    data class Text(
        val text: String
    ) : ToolContent()

    @Serializable
    @SerialName("json")
    data class Json(
        val data: JsonElement
    ) : ToolContent()
}

@Serializable
data class ToolListResponse(
    val tools: List<ToolDefinition>
)

@Serializable
sealed class McpError(
    val code: Int,
    override val message: String
) : Exception(message) {

    @Serializable
    class ToolNotFound(val toolName: String) : McpError(
        code = -32601,
        message = "Tool not found: $toolName"
    )

    @Serializable
    class InvalidArguments(val detail: String) : McpError(
        code = -32602,
        message = "Invalid arguments: $detail"
    )

    @Serializable
    class InternalError(val detail: String) : McpError(
        code = -32603,
        message = "Internal error: $detail"
    )

    @Serializable
    class Unauthorized(val detail: String = "Authentication required") : McpError(
        code = -32001,
        message = detail
    )
}

@Serializable
data class McpErrorResponse(
    val code: Int,
    val message: String
) {
    companion object {
        fun from(error: McpError): McpErrorResponse =
            McpErrorResponse(code = error.code, message = error.message)
    }
}
