package com.forge.adapter.model

/**
 * Events emitted during a streaming completion with tool calling support.
 *
 * Maps to Claude API SSE events:
 * - message_start → MessageStart
 * - content_block_start (type=text) → implicit (followed by ContentDelta)
 * - content_block_delta (type=text_delta) → ContentDelta
 * - content_block_start (type=tool_use) → ToolUseStart
 * - content_block_delta (type=input_json_delta) → ToolInputDelta
 * - content_block_stop → ToolUseEnd
 * - message_delta → MessageDelta
 * - message_stop → MessageStop
 */
sealed class StreamEvent {
    /** Emitted when a new message begins streaming. */
    data class MessageStart(val messageId: String, val model: String) : StreamEvent()

    /** A chunk of text content from the model. */
    data class ContentDelta(val text: String) : StreamEvent()

    /** A tool use content block has started. */
    data class ToolUseStart(val index: Int, val id: String, val name: String) : StreamEvent()

    /** A partial JSON fragment for tool input. Accumulate to build full input. */
    data class ToolInputDelta(val partialJson: String) : StreamEvent()

    /** A tool use content block has finished. */
    data class ToolUseEnd(val index: Int) : StreamEvent()

    /** Message-level metadata update (e.g., stop reason). */
    data class MessageDelta(val stopReason: StopReason?) : StreamEvent()

    /** The message has fully completed. */
    data object MessageStop : StreamEvent()

    /** An error occurred during streaming. */
    data class Error(val message: String) : StreamEvent()
}
