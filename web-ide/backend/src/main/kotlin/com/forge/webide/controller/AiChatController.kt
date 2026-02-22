package com.forge.webide.controller

import com.forge.webide.entity.ChatSessionEntity
import com.forge.webide.model.*
import com.forge.webide.repository.ChatMessageRepository
import com.forge.webide.repository.ChatSessionRepository
import com.forge.webide.service.ClaudeAgentService
import com.forge.webide.service.skill.HitlAction
import com.forge.webide.service.skill.HitlDecision
import com.forge.webide.service.skill.SkillLoader
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.security.Principal
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/chat")
class AiChatController(
    private val claudeAgentService: ClaudeAgentService,
    private val chatSessionRepository: ChatSessionRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val skillLoader: SkillLoader
) {

    @PostMapping("/sessions")
    fun createSession(
        @RequestBody request: CreateChatSessionRequest,
        principal: Principal?
    ): ResponseEntity<ChatSession> {
        val userId = principal?.name ?: "anonymous"
        val sessionId = UUID.randomUUID().toString()

        val entity = ChatSessionEntity(
            id = sessionId,
            workspaceId = request.workspaceId,
            userId = userId,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        chatSessionRepository.save(entity)

        val session = ChatSession(
            id = sessionId,
            workspaceId = request.workspaceId,
            userId = userId,
            createdAt = entity.createdAt
        )
        return ResponseEntity.status(HttpStatus.CREATED).body(session)
    }

    @Transactional(readOnly = true)
    @GetMapping("/sessions/{sessionId}/messages")
    fun getMessages(
        @PathVariable sessionId: String
    ): ResponseEntity<List<ChatMessage>> {
        if (!chatSessionRepository.existsById(sessionId)) {
            return ResponseEntity.notFound().build()
        }

        val messages = chatMessageRepository.findBySessionIdOrderByCreatedAt(sessionId).map { entity ->
            ChatMessage(
                id = entity.id,
                sessionId = entity.sessionId,
                role = when (entity.role) {
                    "user" -> MessageRole.USER
                    "assistant" -> MessageRole.ASSISTANT
                    else -> MessageRole.USER
                },
                content = entity.content,
                timestamp = entity.createdAt,
                toolCalls = entity.toolCalls.map { tc ->
                    ToolCallRecord(
                        id = tc.id,
                        name = tc.toolName,
                        input = emptyMap(), // Stored as string; could parse JSON
                        output = tc.output,
                        status = tc.status
                    )
                }.ifEmpty { null }
            )
        }
        return ResponseEntity.ok(messages)
    }

    @PostMapping("/sessions/{sessionId}/messages")
    fun sendMessage(
        @PathVariable sessionId: String,
        @RequestBody request: ChatStreamMessage,
        principal: Principal?
    ): ResponseEntity<ChatMessage> {
        val session = chatSessionRepository.findById(sessionId).orElse(null)
            ?: return ResponseEntity.notFound().build()

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

        return ResponseEntity.ok(assistantMessage)
    }

    /**
     * Server-Sent Events endpoint for streaming chat responses (primary communication channel).
     * The model name and workspaceId are passed in the request body.
     */
    @PostMapping("/sessions/{sessionId}/stream", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun streamMessage(
        @PathVariable sessionId: String,
        @RequestBody request: ChatStreamMessage,
        principal: Principal?
    ): SseEmitter {
        val session = chatSessionRepository.findById(sessionId).orElse(null)
            ?: throw IllegalArgumentException("Session not found: $sessionId")

        val userId = principal?.name ?: "anonymous"
        val workspaceId = request.workspaceId.ifBlank { session.workspaceId }
        val emitter = SseEmitter(300_000L) // 5-minute timeout

        claudeAgentService.streamMessage(
            sessionId = sessionId,
            message = request.content,
            contexts = request.contexts ?: emptyList(),
            workspaceId = workspaceId,
            model = request.model,
            userId = userId,
            onEvent = { event ->
                try {
                    emitter.send(SseEmitter.event().data(event))
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            },
            onComplete = { _ ->
                try {
                    emitter.send(SseEmitter.event().data(mapOf("type" to "done")))
                    emitter.complete()
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            },
            onError = { error ->
                try {
                    emitter.send(SseEmitter.event().data(mapOf("type" to "error", "content" to error.message)))
                    emitter.completeWithError(error)
                } catch (e: Exception) {
                    emitter.completeWithError(e)
                }
            }
        )

        return emitter
    }

    /**
     * HITL (Human-in-the-Loop) response endpoint.
     * Called by the client to approve, reject, or modify an AI action during a checkpoint.
     */
    @PostMapping("/sessions/{sessionId}/hitl")
    fun submitHitlResponse(
        @PathVariable sessionId: String,
        @RequestBody request: HitlResponseRequest
    ): ResponseEntity<Void> {
        val action = try {
            HitlAction.valueOf(request.action.uppercase())
        } catch (e: IllegalArgumentException) {
            return ResponseEntity.badRequest().build()
        }

        claudeAgentService.resolveCheckpoint(
            sessionId,
            HitlDecision(
                action = action,
                feedback = request.feedback,
                modifiedPrompt = request.modifiedPrompt
            )
        )
        return ResponseEntity.ok().build()
    }

    @GetMapping("/skills")
    fun listSkills(): ResponseEntity<List<Map<String, Any?>>> {
        val skills = skillLoader.loadAllSkills().map { skill ->
            mapOf(
                "name" to skill.name,
                "description" to skill.description,
                "tags" to skill.tags,
                "trigger" to skill.trigger,
                "sourcePath" to skill.sourcePath
            )
        }
        return ResponseEntity.ok(skills)
    }

    @GetMapping("/profiles")
    fun listProfiles(): ResponseEntity<List<Map<String, Any?>>> {
        val profiles = skillLoader.loadAllProfiles().map { profile ->
            mapOf(
                "name" to profile.name,
                "description" to profile.description,
                "skills" to profile.skills,
                "baselines" to profile.baselines,
                "hitlCheckpoint" to profile.hitlCheckpoint
            )
        }
        return ResponseEntity.ok(profiles)
    }
}
