package com.forge.mcp.knowledge

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Local file-based knowledge provider that reads from a directory of markdown files.
 * Used when KNOWLEDGE_MODE=local (no external wiki dependency).
 */
class LocalKnowledgeProvider(private val basePath: String) {

    private val logger = LoggerFactory.getLogger(LocalKnowledgeProvider::class.java)

    data class KnowledgeDocument(
        val title: String,
        val category: String,
        val path: String,
        val content: String,
        val excerpt: String
    )

    @Volatile
    private var documents: List<KnowledgeDocument> = indexDocuments()

    private fun indexDocuments(): List<KnowledgeDocument> {
        val baseDir = File(basePath)
        if (!baseDir.exists() || !baseDir.isDirectory) {
            logger.warn("Knowledge base path does not exist: {}", basePath)
            return emptyList()
        }

        val docs = baseDir.walkTopDown()
            .filter { it.isFile && it.extension == "md" }
            .map { file ->
                val content = file.readText()
                val relativePath = file.relativeTo(baseDir).path
                val category = file.parentFile?.name ?: "general"
                val title = extractTitle(content, file.nameWithoutExtension)
                val excerpt = extractExcerpt(content)

                KnowledgeDocument(
                    title = title,
                    category = category,
                    path = relativePath,
                    content = content,
                    excerpt = excerpt
                )
            }.toList()

        logger.info("Indexed {} knowledge documents from {}", docs.size, basePath)
        return docs
    }

    private fun extractTitle(content: String, fallback: String): String {
        val firstLine = content.lineSequence().firstOrNull { it.isNotBlank() } ?: return fallback
        return if (firstLine.startsWith("# ")) {
            firstLine.removePrefix("# ").trim()
        } else {
            fallback
        }
    }

    private fun extractExcerpt(content: String, maxLength: Int = 200): String {
        return content.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("---") }
            .joinToString(" ")
            .take(maxLength)
            .trim()
    }

    fun search(query: String, category: String? = null, limit: Int = 10): List<KnowledgeDocument> {
        val queryLower = query.lowercase()
        val keywords = queryLower.split("\\s+".toRegex()).filter { it.length >= 2 }

        return documents
            .filter { doc ->
                val matchesCategory = category.isNullOrBlank() || doc.category.equals(category, ignoreCase = true)
                val contentLower = (doc.title + " " + doc.content).lowercase()
                val matchesQuery = keywords.any { keyword -> contentLower.contains(keyword) }
                matchesCategory && matchesQuery
            }
            .sortedByDescending { doc ->
                val contentLower = (doc.title + " " + doc.content).lowercase()
                keywords.count { keyword -> contentLower.contains(keyword) }
            }
            .take(limit)
    }

    fun getDocument(path: String): KnowledgeDocument? {
        return documents.find { it.path == path }
    }

    fun listByCategory(category: String): List<KnowledgeDocument> {
        return documents.filter { it.category.equals(category, ignoreCase = true) }
    }

    fun allDocuments(): List<KnowledgeDocument> = documents

    /** Re-index documents after new files are created. */
    fun reload() {
        documents = indexDocuments()
    }
}
