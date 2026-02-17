package com.forge.webide.repository

import com.forge.webide.entity.ChatMessageEntity
import com.forge.webide.entity.ChatSessionEntity
import com.forge.webide.entity.ToolCallEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import java.time.Instant

/**
 * @DataJpaTest for chat persistence repositories.
 *
 * Verifies CRUD operations, queries, and Flyway migration correctness
 * using an in-memory H2 database.
 */
@DataJpaTest
class ChatRepositoryTest {

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @Autowired
    private lateinit var chatSessionRepository: ChatSessionRepository

    @Autowired
    private lateinit var chatMessageRepository: ChatMessageRepository

    @Test
    fun `save and find ChatSession by id`() {
        val session = ChatSessionEntity(
            id = "session-1",
            workspaceId = "ws-1",
            userId = "user-1",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        chatSessionRepository.save(session)
        entityManager.flush()
        entityManager.clear()

        val found = chatSessionRepository.findById("session-1")
        assertThat(found).isPresent
        assertThat(found.get().workspaceId).isEqualTo("ws-1")
        assertThat(found.get().userId).isEqualTo("user-1")
    }

    @Test
    fun `findByWorkspaceId returns matching sessions`() {
        chatSessionRepository.save(ChatSessionEntity(id = "s1", workspaceId = "ws-A", userId = "u1"))
        chatSessionRepository.save(ChatSessionEntity(id = "s2", workspaceId = "ws-A", userId = "u2"))
        chatSessionRepository.save(ChatSessionEntity(id = "s3", workspaceId = "ws-B", userId = "u1"))
        entityManager.flush()

        val results = chatSessionRepository.findByWorkspaceId("ws-A")
        assertThat(results).hasSize(2)
        assertThat(results.map { it.id }).containsExactlyInAnyOrder("s1", "s2")
    }

    @Test
    fun `findByUserId returns matching sessions`() {
        chatSessionRepository.save(ChatSessionEntity(id = "s1", workspaceId = "ws-1", userId = "alice"))
        chatSessionRepository.save(ChatSessionEntity(id = "s2", workspaceId = "ws-2", userId = "bob"))
        chatSessionRepository.save(ChatSessionEntity(id = "s3", workspaceId = "ws-3", userId = "alice"))
        entityManager.flush()

        val results = chatSessionRepository.findByUserId("alice")
        assertThat(results).hasSize(2)
    }

    @Test
    fun `save and retrieve ChatMessage`() {
        val session = ChatSessionEntity(id = "session-1", workspaceId = "ws-1")
        chatSessionRepository.save(session)

        val message = ChatMessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            role = "user",
            content = "Hello, how are you?"
        )
        chatMessageRepository.save(message)
        entityManager.flush()
        entityManager.clear()

        val found = chatMessageRepository.findById("msg-1")
        assertThat(found).isPresent
        assertThat(found.get().content).isEqualTo("Hello, how are you?")
        assertThat(found.get().role).isEqualTo("user")
    }

    @Test
    fun `findBySessionIdOrderByCreatedAt returns messages in chronological order`() {
        val session = ChatSessionEntity(id = "session-1", workspaceId = "ws-1")
        chatSessionRepository.save(session)

        val now = Instant.now()
        chatMessageRepository.save(ChatMessageEntity(
            id = "msg-1", sessionId = "session-1", role = "user",
            content = "First message", createdAt = now.minusSeconds(30)
        ))
        chatMessageRepository.save(ChatMessageEntity(
            id = "msg-2", sessionId = "session-1", role = "assistant",
            content = "Response", createdAt = now.minusSeconds(20)
        ))
        chatMessageRepository.save(ChatMessageEntity(
            id = "msg-3", sessionId = "session-1", role = "user",
            content = "Follow-up", createdAt = now.minusSeconds(10)
        ))
        entityManager.flush()

        val messages = chatMessageRepository.findBySessionIdOrderByCreatedAt("session-1")
        assertThat(messages).hasSize(3)
        assertThat(messages[0].id).isEqualTo("msg-1")
        assertThat(messages[1].id).isEqualTo("msg-2")
        assertThat(messages[2].id).isEqualTo("msg-3")
    }

    @Test
    fun `findBySessionIdOrderByCreatedAt returns empty for unknown session`() {
        val messages = chatMessageRepository.findBySessionIdOrderByCreatedAt("nonexistent")
        assertThat(messages).isEmpty()
    }

    @Test
    fun `ChatMessage with ToolCalls persists via cascade`() {
        val session = ChatSessionEntity(id = "session-1", workspaceId = "ws-1")
        chatSessionRepository.save(session)

        val message = ChatMessageEntity(
            id = "msg-1",
            sessionId = "session-1",
            role = "assistant",
            content = "Let me search."
        )

        message.toolCalls.add(ToolCallEntity(
            id = "tc-1",
            messageId = "msg-1",
            toolName = "search_knowledge",
            input = """{"query":"spring boot"}""",
            output = "Found 3 docs",
            status = "complete",
            durationMs = 150
        ))

        chatMessageRepository.save(message)
        entityManager.flush()
        entityManager.clear()

        val found = chatMessageRepository.findById("msg-1")
        assertThat(found).isPresent
        assertThat(found.get().toolCalls).hasSize(1)
        assertThat(found.get().toolCalls[0].toolName).isEqualTo("search_knowledge")
        assertThat(found.get().toolCalls[0].output).isEqualTo("Found 3 docs")
        assertThat(found.get().toolCalls[0].durationMs).isEqualTo(150)
    }

    @Test
    fun `session existsById works correctly`() {
        chatSessionRepository.save(ChatSessionEntity(id = "exists", workspaceId = "ws-1"))
        entityManager.flush()

        assertThat(chatSessionRepository.existsById("exists")).isTrue()
        assertThat(chatSessionRepository.existsById("doesnt-exist")).isFalse()
    }

    @Test
    fun `findByWorkspaceIdAndUserId returns filtered results`() {
        chatSessionRepository.save(ChatSessionEntity(id = "s1", workspaceId = "ws-A", userId = "alice"))
        chatSessionRepository.save(ChatSessionEntity(id = "s2", workspaceId = "ws-A", userId = "bob"))
        chatSessionRepository.save(ChatSessionEntity(id = "s3", workspaceId = "ws-B", userId = "alice"))
        entityManager.flush()

        val results = chatSessionRepository.findByWorkspaceIdAndUserId("ws-A", "alice")
        assertThat(results).hasSize(1)
        assertThat(results[0].id).isEqualTo("s1")
    }
}
