package com.forge.webide.controller

import com.forge.webide.model.*
import com.forge.webide.service.ClaudeAgentService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.security.Principal
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/chat")
class AiChatController(
    private val claudeAgentService: ClaudeAgentService
) {
    private val sessions = ConcurrentHashMap<String, ChatSession>()
    private val messageStore = ConcurrentHashMap<String, MutableList<ChatMessage>>()

    @PostMapping("/sessions")
    fun createSession(
        @RequestBody request: CreateChatSessionRequest,
        principal: Principal?
    ): ResponseEntity<ChatSession> {
        val userId = principal?.name ?: "anonymous"
        val session = ChatSession(
            workspaceId = request.workspaceId,
            userId = userId
        )
        sessions[session.id] = session
        messageStore[session.id] = mutableListOf()
        return ResponseEntity.status(HttpStatus.CREATED).body(session)
    }

    @GetMapping("/sessions/{sessionId}/messages")
    fun getMessages(
        @PathVariable sessionId: String
    ): ResponseEntity<List<ChatMessage>> {
        val messages = messageStore[sessionId]
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(messages)
    }

    @PostMapping("/sessions/{sessionId}/messages")
    fun sendMessage(
        @PathVariable sessionId: String,
        @RequestBody request: ChatStreamMessage,
        principal: Principal?
    ): ResponseEntity<ChatMessage> {
        val session = sessions[sessionId]
            ?: return ResponseEntity.notFound().build()

        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = request.content,
            contexts = request.contexts
        )
        messageStore[sessionId]?.add(userMessage)

        val response = claudeAgentService.sendMessage(
            sessionId = sessionId,
            message = request.content,
            contexts = request.contexts ?: emptyList(),
            workspaceId = session.workspaceId
        )

        val assistantMessage = ChatMessage(
            sessionId = sessionId,
            role = MessageRole.ASSISTANT,
            content = response.content,
            toolCalls = response.toolCalls
        )
        messageStore[sessionId]?.add(assistantMessage)

        return ResponseEntity.ok(assistantMessage)
    }

    /**
     * Server-Sent Events endpoint for streaming chat responses.
     * This serves as the HTTP fallback when WebSocket is unavailable.
     */
    @PostMapping("/sessions/{sessionId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMessage(
        @PathVariable sessionId: String,
        @RequestBody request: ChatStreamMessage,
        principal: Principal?
    ): SseEmitter {
        val session = sessions[sessionId]
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val emitter = SseEmitter(300_000L) // 5-minute timeout

        // Store user message
        val userMessage = ChatMessage(
            sessionId = sessionId,
            role = MessageRole.USER,
            content = request.content,
            contexts = request.contexts
        )
        messageStore[sessionId]?.add(userMessage)

        // Stream response asynchronously
        claudeAgentService.streamMessage(
            sessionId = sessionId,
            message = request.content,
            contexts = request.contexts ?: emptyList(),
            workspaceId = session.workspaceId,
            onEvent = { event ->
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("message")
                            .data(event)
                    )
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            },
            onComplete = { assistantMessage ->
                messageStore[sessionId]?.add(assistantMessage)
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("message")
                            .data(mapOf("type" to "done"))
                    )
                    emitter.complete()
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            },
            onError = { error ->
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("message")
                            .data(mapOf("type" to "error", "content" to error.message))
                    )
                    emitter.completeWithError(error)
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            }
        )

        return emitter
    }
}
