package com.forge.webide.controller

import com.forge.webide.model.McpTool
import com.forge.webide.model.McpToolCallRequest
import com.forge.webide.model.McpToolCallResponse
import com.forge.webide.service.McpProxyService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * REST controller exposing MCP tool operations to the frontend.
 *
 * Provides endpoints for listing available tools, calling tools, and
 * invalidating the tool cache.
 */
@RestController
@RequestMapping("/api/mcp")
class McpController(
    private val mcpProxyService: McpProxyService
) {

    @GetMapping("/tools")
    fun listTools(): ResponseEntity<List<McpTool>> {
        return ResponseEntity.ok(mcpProxyService.listTools())
    }

    @PostMapping("/tools/call")
    fun callTool(@RequestBody request: McpToolCallRequest): ResponseEntity<McpToolCallResponse> {
        val result = mcpProxyService.callTool(request.name, request.arguments)
        return ResponseEntity.ok(result)
    }

    @PostMapping("/tools/cache/invalidate")
    fun invalidateCache(): ResponseEntity<Map<String, String>> {
        mcpProxyService.invalidateCache()
        return ResponseEntity.ok(mapOf("status" to "cache_invalidated"))
    }
}
