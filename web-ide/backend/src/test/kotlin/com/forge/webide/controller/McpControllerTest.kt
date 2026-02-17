package com.forge.webide.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.forge.webide.model.McpContent
import com.forge.webide.model.McpTool
import com.forge.webide.model.McpToolCallResponse
import com.forge.webide.service.McpProxyService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

/**
 * @WebMvcTest for McpController.
 *
 * Tests REST endpoints for MCP tool operations.
 * Security filters are disabled to test controller logic in isolation.
 */
@WebMvcTest(McpController::class)
@AutoConfigureMockMvc(addFilters = false)
class McpControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var mcpProxyService: McpProxyService

    @TestConfiguration
    class Config {
        @Bean
        fun mcpProxyService(): McpProxyService = mockk(relaxed = true)
    }

    @Test
    fun `GET tools returns list of available tools`() {
        every { mcpProxyService.listTools() } returns listOf(
            McpTool("search_knowledge", "Search the knowledge base", mapOf("type" to "object")),
            McpTool("read_file", "Read a file", mapOf("type" to "object"))
        )

        mockMvc.perform(get("/api/mcp/tools"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("search_knowledge"))
            .andExpect(jsonPath("$[1].name").value("read_file"))
    }

    @Test
    fun `POST tools call invokes tool and returns result`() {
        every { mcpProxyService.callTool("search_knowledge", any()) } returns McpToolCallResponse(
            content = listOf(McpContent(type = "text", text = "Found 3 documents")),
            isError = false
        )

        val request = mapOf("name" to "search_knowledge", "arguments" to mapOf("query" to "spring"))

        mockMvc.perform(
            post("/api/mcp/tools/call")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isError").value(false))
            .andExpect(jsonPath("$.content[0].text").value("Found 3 documents"))
    }

    @Test
    fun `POST cache invalidate clears cache`() {
        mockMvc.perform(
            post("/api/mcp/tools/cache/invalidate")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("cache_invalidated"))

        verify { mcpProxyService.invalidateCache() }
    }
}
