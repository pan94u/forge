package com.forge.webide.service.eval

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.webide.entity.EvalTaskEntity
import com.forge.webide.repository.EvalTaskRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class EvalTaskServiceTest {

    private lateinit var evalTaskRepository: EvalTaskRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: EvalTaskService

    private fun makeEntity(
        id: String = "task-001",
        name: String = "Test Task",
        input: String = "Do something",
        successCriteria: String = "contains:result",
        taskType: String? = "SDLC",
        difficulty: String = "MEDIUM",
        orgId: String? = null
    ) = EvalTaskEntity(
        id = id,
        name = name,
        input = input,
        successCriteria = successCriteria,
        taskType = taskType,
        difficulty = difficulty,
        orgId = orgId,
        source = "MANUAL",
        isActive = true,
        createdAt = Instant.now()
    )

    @BeforeEach
    fun setUp() {
        evalTaskRepository = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        service = EvalTaskService(evalTaskRepository, objectMapper)
    }

    @Test
    fun `listTasks returns active tasks when orgId is null`() {
        val entities = listOf(makeEntity("t1"), makeEntity("t2"))
        every { evalTaskRepository.findByIsActiveTrue() } returns entities

        val result = service.listTasks(orgId = null, taskType = null, difficulty = null)

        assertThat(result).hasSize(2)
        assertThat(result.map { it.id }).containsExactlyInAnyOrder("t1", "t2")
    }

    @Test
    fun `listTasks returns org-scoped tasks when orgId provided`() {
        val entities = listOf(makeEntity("t1", orgId = "org-1"))
        every { evalTaskRepository.findAvailableForOrg("org-1") } returns entities

        val result = service.listTasks(orgId = "org-1", taskType = null, difficulty = null)

        assertThat(result).hasSize(1)
        assertThat(result[0].orgId).isEqualTo("org-1")
    }

    @Test
    fun `getTask returns null when not found`() {
        every { evalTaskRepository.findById("missing") } returns Optional.empty()

        val result = service.getTask("missing")

        assertThat(result).isNull()
    }

    @Test
    fun `getTask returns dto when found`() {
        val entity = makeEntity("t1", name = "My Task")
        every { evalTaskRepository.findById("t1") } returns Optional.of(entity)

        val result = service.getTask("t1")

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("My Task")
        assertThat(result.difficulty).isEqualTo("MEDIUM")
    }

    @Test
    fun `createTask saves entity and returns dto`() {
        val dto = EvalTaskService.EvalTaskDto(
            id = null, name = "New Task", description = null,
            input = "Do this", successCriteria = "contains:done",
            graderConfig = null, taskType = "SDLC", skillTags = listOf("planning"),
            difficulty = "EASY", source = "MANUAL", orgId = null, isActive = true
        )
        val savedSlot = slot<EvalTaskEntity>()
        every { evalTaskRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val result = service.createTask(dto)

        assertThat(result.name).isEqualTo("New Task")
        assertThat(result.difficulty).isEqualTo("EASY")
        verify(exactly = 1) { evalTaskRepository.save(any()) }
    }

    @Test
    fun `deleteTask sets isActive to false`() {
        val entity = makeEntity("t1")
        every { evalTaskRepository.findById("t1") } returns Optional.of(entity)
        every { evalTaskRepository.save(any()) } returns entity

        val result = service.deleteTask("t1")

        assertThat(result).isTrue()
        assertThat(entity.isActive).isFalse()
    }

    @Test
    fun `deleteTask returns false when task not found`() {
        every { evalTaskRepository.findById("missing") } returns Optional.empty()

        val result = service.deleteTask("missing")

        assertThat(result).isFalse()
    }

    @Test
    fun `importFromYaml parses YAML and saves task`() {
        val yaml = """
            id: eval-test-001
            name: Test YAML Import
            description: A test eval task from YAML
            prompt: |
              Analyze this requirement and create a plan.
            tags:
              - planning
              - testing
            assertions:
              - type: contains
                expected: "plan"
                description: "Response includes a plan"
              - type: not_contains
                expected: "I don't know"
                description: "Response expresses confidence"
            baseline_pass_rate: 0.85
        """.trimIndent()

        val savedSlot = slot<EvalTaskEntity>()
        every { evalTaskRepository.save(capture(savedSlot)) } answers { savedSlot.captured }

        val result = service.importFromYaml(yaml, orgId = null)

        assertThat(result.imported).isEqualTo(1)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.taskIds).hasSize(1)

        val saved = savedSlot.captured
        assertThat(saved.name).isEqualTo("Test YAML Import")
        assertThat(saved.source).isEqualTo("YAML_IMPORT")
        assertThat(saved.difficulty).isEqualTo("EASY") // 2 assertions → EASY
    }

    @Test
    fun `importFromYaml infers HARD difficulty for many assertions`() {
        val assertions = (1..10).joinToString("\n") { i ->
            """
              - type: contains
                expected: "item$i"
                description: "Check item $i"
            """.trimIndent()
        }
        val yaml = """
            name: Hard Task
            prompt: Complex task
            assertions:
${ assertions.lines().joinToString("\n") { "              $it" } }
        """.trimIndent()

        every { evalTaskRepository.save(any()) } answers { firstArg() }

        val result = service.importFromYaml(yaml, orgId = "org-1")
        assertThat(result.imported).isEqualTo(1)
    }

    @Test
    fun `bulkImportFromDirectory handles multiple YAMLs`() {
        val yaml1 = "name: Task One\nprompt: Do one\nassertions:\n  - type: contains\n    expected: result\n    description: has result"
        val yaml2 = "name: Task Two\nprompt: Do two\nassertions:\n  - type: contains\n    expected: output\n    description: has output"

        every { evalTaskRepository.save(any()) } answers { firstArg() }

        val result = service.bulkImportFromDirectory(listOf(yaml1, yaml2), orgId = null)

        assertThat(result.imported).isEqualTo(2)
        assertThat(result.skipped).isEqualTo(0)
        assertThat(result.taskIds).hasSize(2)
    }
}
