package com.forge.webide.controller

import com.forge.webide.service.McpProxyService
import com.forge.webide.service.WorkspaceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Provides unified context search for the ContextPicker UI component.
 * Searches across workspace files, knowledge base, database schemas, and services.
 */
@RestController
@RequestMapping("/api/context")
class ContextController(
    private val workspaceService: WorkspaceService,
    private val mcpProxyService: McpProxyService
) {

    data class ContextItem(
        val id: String,
        val type: String,
        val label: String,
        val description: String? = null,
        val preview: String? = null
    )

    @GetMapping("/search")
    fun search(
        @RequestParam category: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) workspaceId: String?
    ): List<ContextItem> {
        return when (category) {
            "files" -> searchWorkspaceFiles(workspaceId, q)
            "knowledge" -> searchKnowledge(q)
            "schema" -> searchSchema(q)
            "services" -> searchServices(q)
            else -> emptyList()
        }
    }

    private fun searchWorkspaceFiles(workspaceId: String?, query: String?): List<ContextItem> {
        if (workspaceId.isNullOrBlank()) return emptyList()

        val tree = workspaceService.getFileTree(workspaceId)
        val items = mutableListOf<ContextItem>()

        fun collectFiles(nodes: List<com.forge.webide.model.FileNode>) {
            for (node in nodes) {
                if (node.type == com.forge.webide.model.FileType.FILE) {
                    val matchesQuery = query.isNullOrBlank() ||
                        node.name.contains(query, ignoreCase = true) ||
                        node.path.contains(query, ignoreCase = true)
                    if (matchesQuery) {
                        items.add(ContextItem(
                            id = node.path,
                            type = "file",
                            label = node.name,
                            description = node.path,
                            preview = workspaceService.getFileContent(workspaceId, node.path)
                                ?.take(200)
                        ))
                    }
                }
                node.children?.let { collectFiles(it) }
            }
        }

        collectFiles(tree)
        return items
    }

    private fun searchKnowledge(query: String?): List<ContextItem> {
        return try {
            val searchQuery = query ?: ""
            val result = mcpProxyService.callTool("search_knowledge", mapOf("query" to searchQuery))
            if (result.isError) return emptyList()

            val text = result.content.firstOrNull()?.text ?: return emptyList()

            // Parse the search results text into ContextItems
            val items = mutableListOf<ContextItem>()
            val lines = text.lines()
            var currentTitle = ""
            var currentPath = ""
            var currentExcerpt = ""

            for (line in lines) {
                when {
                    line.matches(Regex("^\\d+\\.\\s+\\*\\*.*\\*\\*.*")) -> {
                        if (currentTitle.isNotBlank()) {
                            items.add(ContextItem(
                                id = currentPath.ifBlank { currentTitle },
                                type = "knowledge",
                                label = currentTitle,
                                description = currentPath,
                                preview = currentExcerpt
                            ))
                        }
                        currentTitle = line.substringAfter("**").substringBefore("**").trim()
                        currentPath = ""
                        currentExcerpt = ""
                    }
                    line.trimStart().startsWith("Path:") -> {
                        currentPath = line.substringAfter("Path:").trim()
                    }
                    line.trim().isNotBlank() && !line.startsWith("Found") -> {
                        if (currentExcerpt.isBlank()) {
                            currentExcerpt = line.trim()
                        }
                    }
                }
            }
            if (currentTitle.isNotBlank()) {
                items.add(ContextItem(
                    id = currentPath.ifBlank { currentTitle },
                    type = "knowledge",
                    label = currentTitle,
                    description = currentPath,
                    preview = currentExcerpt
                ))
            }

            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchSchema(query: String?): List<ContextItem> {
        return try {
            val result = mcpProxyService.callTool("query_schema", mapOf("table" to (query ?: "")))
            if (result.isError) return emptyList()

            val text = result.content.firstOrNull()?.text ?: return emptyList()

            if (query.isNullOrBlank()) {
                // Parse table list
                text.lines()
                    .filter { it.trimStart().startsWith("- ") }
                    .map { line ->
                        val tableName = line.trimStart().removePrefix("- ").trim()
                        ContextItem(
                            id = tableName,
                            type = "schema",
                            label = tableName,
                            description = "Database table"
                        )
                    }
            } else {
                listOf(ContextItem(
                    id = query,
                    type = "schema",
                    label = query,
                    description = "Table schema",
                    preview = text.take(200)
                ))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchServices(query: String?): List<ContextItem> {
        return try {
            val result = mcpProxyService.callTool("get_service_info", mapOf("service" to (query ?: "")))
            if (result.isError) return emptyList()

            val text = result.content.firstOrNull()?.text ?: return emptyList()

            // Parse service info blocks
            val services = mutableListOf<ContextItem>()
            val blocks = text.split("===").filter { it.isNotBlank() }

            var i = 0
            while (i < blocks.size - 1) {
                val name = blocks[i].trim()
                val info = blocks[i + 1].trim()
                services.add(ContextItem(
                    id = name,
                    type = "service",
                    label = name,
                    description = "Platform service",
                    preview = info.take(200)
                ))
                i += 2
            }

            services
        } catch (e: Exception) {
            emptyList()
        }
    }
}
