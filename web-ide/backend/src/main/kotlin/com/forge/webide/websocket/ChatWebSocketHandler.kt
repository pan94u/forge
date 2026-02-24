package com.forge.webide.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.webide.model.*
import com.forge.webide.service.ClaudeAgentService
import com.forge.webide.service.skill.HitlAction
import com.forge.webide.service.skill.HitlDecision
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap

/**
 * WebSocket handler for real-time AI chat streaming.
 *
 * Manages WebSocket sessions for chat, receives user messages,
 * and streams AI responses back in real-time with support for
 * content chunks, tool_use events, tool_result events, and
 * multi-turn agentic loop progress.
 */
@Component
class ChatWebSocketHandler(
    private val claudeAgentService: ClaudeAgentService,
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(ChatWebSocketHandler::class.java)

    // Map of WebSocket session ID to chat session ID
    private val sessionMapping = ConcurrentHashMap<String, String>()

    // Active WebSocket sessions
    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val chatSessionId = extractSessionId(session)
        sessionMapping[session.id] = chatSessionId
        activeSessions[session.id] = session

        logger.info("Chat WebSocket connected: ws=${session.id}, chat=$chatSessionId")

        // Send connection acknowledgment
        sendMessage(session, mapOf(
            "type" to "connected",
            "sessionId" to chatSessionId
        ))

        // Check for pending HITL checkpoint (reconnection recovery)
        val pendingCheckpoint = claudeAgentService.getPendingCheckpoint(chatSessionId)
        if (pendingCheckpoint != null) {
            logger.info("Resending pending HITL checkpoint for session $chatSessionId")
            sendMessage(session, mapOf(
                "type" to "hitl_checkpoint",
                "status" to "awaiting_approval",
                "profile" to pendingCheckpoint.profile,
                "checkpoint" to pendingCheckpoint.checkpoint,
                "deliverables" to (try { com.google.gson.Gson().fromJson(pendingCheckpoint.deliverables, List::class.java) } catch (_: Exception) { emptyList<String>() }),
                "timeoutSeconds" to 300
            ))
        }
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        val chatSessionId = sessionMapping[session.id] ?: return

        try {
            val payload = objectMapper.readValue(message.payload, Map::class.java)
            val type = payload["type"] as? String

            when (type) {
                "message" -> handleChatMessage(session, chatSessionId, payload)
                "hitl_response" -> handleHitlResponse(chatSessionId, payload)
                "intent_response" -> handleIntentResponse(chatSessionId, payload)
                "ping" -> sendMessage(session, mapOf("type" to "pong"))
                else -> {
                    logger.warn("Unknown message type: $type")
                    sendMessage(session, mapOf(
                        "type" to "error",
                        "content" to "Unknown message type: $type"
                    ))
                }
            }
        } catch (e: Exception) {
            logger.error("Error handling WebSocket message: ${e.message}", e)
            sendMessage(session, mapOf(
                "type" to "error",
                "content" to "Failed to process message: ${e.message}"
            ))
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        val chatSessionId = sessionMapping.remove(session.id)
        activeSessions.remove(session.id)
        logger.info("Chat WebSocket disconnected: ws=${session.id}, chat=$chatSessionId, status=$status")
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Chat WebSocket transport error: ws=${session.id}, error=${exception.message}")
        sessionMapping.remove(session.id)
        activeSessions.remove(session.id)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleChatMessage(
        wsSession: WebSocketSession,
        chatSessionId: String,
        payload: Map<*, *>
    ) {
        val content = payload["content"] as? String ?: return
        val workspaceId = payload["workspaceId"] as? String ?: ""
        val modelId = payload["modelId"] as? String
        val rawContexts = payload["contexts"] as? List<Map<String, Any?>> ?: emptyList()

        val contexts = rawContexts.map { ctx ->
            ContextReference(
                type = ctx["type"] as? String ?: "",
                id = ctx["id"] as? String ?: "",
                content = ctx["content"] as? String
            )
        }

        logger.debug("Chat message received: session=$chatSessionId, workspace=$workspaceId, length=${content.length}")

        // Stream the response back via the agentic loop
        claudeAgentService.streamMessage(
            sessionId = chatSessionId,
            message = content,
            contexts = contexts,
            workspaceId = workspaceId,
            modelId = modelId,
            onEvent = { event ->
                // Forward all event types: content, tool_use_start, tool_use, tool_result, error
                if (wsSession.isOpen) {
                    sendMessage(wsSession, event)
                }
            },
            onComplete = { assistantMessage ->
                // Send the final "done" event only after all agentic turns are finished
                if (wsSession.isOpen) {
                    sendMessage(wsSession, mapOf(
                        "type" to "done",
                        "messageId" to assistantMessage.id
                    ))
                }
            },
            onError = { error ->
                if (wsSession.isOpen) {
                    sendMessage(wsSession, mapOf(
                        "type" to "error",
                        "content" to (error.message ?: "Unknown error")
                    ))
                }
            }
        )
    }

    private fun handleHitlResponse(chatSessionId: String, payload: Map<*, *>) {
        val actionStr = payload["action"] as? String ?: return
        val feedback = payload["feedback"] as? String
        val modifiedPrompt = payload["modifiedPrompt"] as? String

        val action = try {
            HitlAction.valueOf(actionStr.uppercase())
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid HITL action: $actionStr")
            return
        }

        val decision = HitlDecision(
            action = action,
            feedback = feedback,
            modifiedPrompt = modifiedPrompt
        )

        claudeAgentService.resolveCheckpoint(chatSessionId, decision)
    }

    private fun handleIntentResponse(chatSessionId: String, payload: Map<*, *>) {
        val selectedProfile = payload["selectedProfile"] as? String
        if (selectedProfile.isNullOrBlank()) {
            logger.warn("Intent response missing selectedProfile for session $chatSessionId")
            return
        }
        logger.info("Intent response received for session {}: {}", chatSessionId, selectedProfile)
        claudeAgentService.resolveIntentConfirmation(chatSessionId, selectedProfile)
    }

    private fun sendMessage(session: WebSocketSession, data: Any) {
        try {
            val json = objectMapper.writeValueAsString(data)
            synchronized(session) {
                if (session.isOpen) {
                    session.sendMessage(TextMessage(json))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to send WebSocket message: ${e.message}")
        }
    }

    private fun extractSessionId(session: WebSocketSession): String {
        val path = session.uri?.path ?: ""
        val segments = path.split("/").filter { it.isNotEmpty() }
        return segments.lastOrNull() ?: session.id
    }
}
