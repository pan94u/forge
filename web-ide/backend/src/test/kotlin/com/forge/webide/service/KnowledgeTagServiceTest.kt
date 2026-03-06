package com.forge.webide.service

import com.forge.webide.entity.KnowledgeTagEntity
import com.forge.webide.model.CreateKnowledgeTagRequest
import com.forge.webide.model.UpdateKnowledgeTagRequest
import com.forge.webide.repository.KnowledgeTagRepository
import com.forge.webide.repository.WorkspaceRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class KnowledgeTagServiceTest {

    private lateinit var repository: KnowledgeTagRepository
    private lateinit var workspaceRepository: WorkspaceRepository
    private lateinit var service: KnowledgeTagService

    private fun makeEntity(
        id: String = "ui-ux",
        name: String = "UI/UX 设计基线",
        sortOrder: Int = 0,
        workspaceId: String? = null,
        tagKey: String? = null
    ) = KnowledgeTagEntity(
        id = id,
        name = name,
        description = "test description",
        chapterHeading = "一、$name",
        content = "# $name\n\nContent here",
        sortOrder = sortOrder,
        status = "active",
        workspaceId = workspaceId,
        tagKey = tagKey
    )

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        workspaceRepository = mockk(relaxed = true)
        // Global templates exist so init doesn't trigger import
        every { repository.findByWorkspaceIdIsNullOrderBySortOrderAsc() } returns listOf(makeEntity())
        // Default: workspaces exist (so tags can be initialized)
        every { workspaceRepository.existsById(any()) } returns true

        service = KnowledgeTagService(repository, "", workspaceRepository)
    }

    @Test
    fun `listTags with null workspaceId returns global templates`() {
        val entities = listOf(
            makeEntity("ui-ux", "UI/UX 设计基线", 0, null, "ui-ux"),
            makeEntity("api-contract", "API 契约基线", 1, null, "api-contract"),
            makeEntity("data-model", "数据模型基线", 2, null, "data-model")
        )
        every { repository.findByWorkspaceIdIsNullOrderBySortOrderAsc() } returns entities

        val result = service.listTags(null)

        assertThat(result).hasSize(3)
        assertThat(result[0].id).isEqualTo("ui-ux")
        assertThat(result[1].id).isEqualTo("api-contract")
        assertThat(result[2].id).isEqualTo("data-model")
    }

    @Test
    fun `listTags with workspaceId returns workspace tags`() {
        val wsId = "ws-123"
        every { repository.countByWorkspaceId(wsId) } returns 8
        val entities = listOf(
            makeEntity("${wsId}_ui-ux", "UI/UX 设计基线", 0, wsId, "ui-ux"),
            makeEntity("${wsId}_api-contract", "API 契约基线", 1, wsId, "api-contract")
        )
        every { repository.findByWorkspaceIdOrderBySortOrderAsc(wsId) } returns entities

        val result = service.listTags(wsId)

        assertThat(result).hasSize(2)
        assertThat(result[0].workspaceId).isEqualTo(wsId)
        assertThat(result[0].tagKey).isEqualTo("ui-ux")
    }

    @Test
    fun `listTags backfills missing tags when count is less than chapterDefs size`() {
        val wsId = "ws-partial"
        every { repository.countByWorkspaceId(wsId) } returns 7  // has 7, needs 8
        // 7 existing tags already present, only flow-diagrams is missing
        every { repository.existsById("${wsId}_ui-ux") } returns true
        every { repository.existsById("${wsId}_api-contract") } returns true
        every { repository.existsById("${wsId}_data-model") } returns true
        every { repository.existsById("${wsId}_arch-decision") } returns true
        every { repository.existsById("${wsId}_verification") } returns true
        every { repository.existsById("${wsId}_frontend-spec") } returns true
        every { repository.existsById("${wsId}_change-rules") } returns true
        every { repository.existsById("${wsId}_flow-diagrams") } returns false
        every { repository.save(any()) } answers { firstArg() }
        every { repository.findByWorkspaceIdOrderBySortOrderAsc(wsId) } returns emptyList()
        every { workspaceRepository.existsById(wsId) } returns true

        service.listTags(wsId)

        // Should have saved exactly 1 missing tag (flow-diagrams)
        verify(exactly = 1) { repository.save(match { it.workspaceId == wsId }) }
    }

    @Test
    fun `listTags auto-initializes workspace tags when count is 0`() {
        val wsId = "ws-new"
        every { repository.countByWorkspaceId(wsId) } returns 0
        every { repository.existsById(any()) } returns false
        every { repository.save(any()) } answers { firstArg() }
        every { repository.findByWorkspaceIdOrderBySortOrderAsc(wsId) } returns emptyList()
        every { workspaceRepository.existsById(wsId) } returns true

        service.listTags(wsId)

        // Should have saved 8 tags (one per chapterDef)
        verify(exactly = 8) { repository.save(match { it.workspaceId == wsId }) }
    }

    @Test
    fun `listTags returns empty list when workspace does not exist (BUG-051)`() {
        val wsId = "ws-deleted"
        every { repository.countByWorkspaceId(wsId) } returns 0
        every { workspaceRepository.existsById(wsId) } returns false

        val result = service.listTags(wsId)

        assertThat(result).isEmpty()
        // Should NOT attempt to initialize tags for a deleted workspace
        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `deleteWorkspaceTags removes all workspace tags`() {
        val wsId = "ws-delete"

        service.deleteWorkspaceTags(wsId)

        verify { repository.deleteByWorkspaceId(wsId) }
    }

    @Test
    fun `getTag returns tag when found`() {
        val entity = makeEntity()
        every { repository.findById("ui-ux") } returns Optional.of(entity)

        val result = service.getTag("ui-ux")

        assertThat(result).isNotNull
        assertThat(result!!.id).isEqualTo("ui-ux")
        assertThat(result.name).isEqualTo("UI/UX 设计基线")
    }

    @Test
    fun `getTag returns null when not found`() {
        every { repository.findById("nonexistent") } returns Optional.empty()

        val result = service.getTag("nonexistent")

        assertThat(result).isNull()
    }

    @Test
    fun `createTag persists and returns new tag`() {
        val request = CreateKnowledgeTagRequest(
            id = "new-tag",
            name = "New Tag",
            description = "A new tag",
            chapterHeading = "New Heading",
            content = "# New Tag\n\nContent",
            sortOrder = 7
        )
        val slot = slot<KnowledgeTagEntity>()
        every { repository.save(capture(slot)) } answers { slot.captured }

        val result = service.createTag(request)

        assertThat(result.id).isEqualTo("new-tag")
        assertThat(result.name).isEqualTo("New Tag")
        assertThat(result.sortOrder).isEqualTo(7)
        verify { repository.save(any()) }
    }

    @Test
    fun `updateTag applies partial updates`() {
        val entity = makeEntity()
        every { repository.findById("ui-ux") } returns Optional.of(entity)
        every { repository.save(any()) } answers { firstArg() }

        val request = UpdateKnowledgeTagRequest(
            name = "Updated Name",
            content = "Updated content"
        )
        val result = service.updateTag("ui-ux", request)

        assertThat(result).isNotNull
        assertThat(result!!.name).isEqualTo("Updated Name")
        assertThat(result.content).isEqualTo("Updated content")
        // description should remain unchanged
        assertThat(result.description).isEqualTo("test description")
    }

    @Test
    fun `updateTag returns null when not found`() {
        every { repository.findById("nonexistent") } returns Optional.empty()

        val result = service.updateTag("nonexistent", UpdateKnowledgeTagRequest(name = "x"))

        assertThat(result).isNull()
    }

    @Test
    fun `deleteTag returns true when exists`() {
        every { repository.existsById("ui-ux") } returns true
        every { repository.deleteById("ui-ux") } just Runs

        val result = service.deleteTag("ui-ux")

        assertThat(result).isTrue()
        verify { repository.deleteById("ui-ux") }
    }

    @Test
    fun `deleteTag returns false when not exists`() {
        every { repository.existsById("nonexistent") } returns false

        val result = service.deleteTag("nonexistent")

        assertThat(result).isFalse()
    }

    @Test
    fun `reorderTags updates sortOrder for each tag`() {
        val entity1 = makeEntity("api-contract", "API 契约基线", 1)
        val entity2 = makeEntity("ui-ux", "UI/UX 设计基线", 0)
        every { repository.findById("api-contract") } returns Optional.of(entity1)
        every { repository.findById("ui-ux") } returns Optional.of(entity2)
        every { repository.save(any()) } answers { firstArg() }
        every { repository.findByWorkspaceIdIsNullOrderBySortOrderAsc() } returns listOf(entity2, entity1)

        // Reorder: api-contract first, then ui-ux
        service.reorderTags(listOf("api-contract", "ui-ux"))

        assertThat(entity1.sortOrder).isEqualTo(0)
        assertThat(entity2.sortOrder).isEqualTo(1)
    }

    @Test
    fun `searchTags returns matching tags`() {
        val entities = listOf(makeEntity("ui-ux", "UI/UX 设计基线", 0))
        every {
            repository.findByNameContainingIgnoreCaseOrContentContainingIgnoreCase("UI", "UI")
        } returns entities

        val result = service.searchTags("UI")

        assertThat(result).hasSize(1)
        assertThat(result[0].name).contains("UI")
    }

    @Test
    fun `searchTags with blank query returns all tags`() {
        val entities = listOf(
            makeEntity("ui-ux", "UI/UX 设计基线", 0),
            makeEntity("api-contract", "API 契约基线", 1)
        )
        every { repository.findByWorkspaceIdIsNullOrderBySortOrderAsc() } returns entities

        val result = service.searchTags("")

        assertThat(result).hasSize(2)
    }

    @Test
    fun `searchTags with workspaceId filters by workspace`() {
        val wsId = "ws-search"
        val entities = listOf(
            makeEntity("${wsId}_ui-ux", "UI/UX 设计基线", 0, wsId, "ui-ux"),
            makeEntity("global-ui", "UI Global", 0, null, null)
        )
        every {
            repository.findByNameContainingIgnoreCaseOrContentContainingIgnoreCase("UI", "UI")
        } returns entities

        val result = service.searchTags("UI", wsId)

        assertThat(result).hasSize(1)
        assertThat(result[0].workspaceId).isEqualTo(wsId)
    }
}
