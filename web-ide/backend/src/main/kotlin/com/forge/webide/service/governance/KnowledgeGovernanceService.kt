package com.forge.webide.service.governance

import com.forge.webide.repository.KnowledgeTagRepository
import org.springframework.stereotype.Service

data class KnowledgeHealth(
    val orgId: String,
    val totalTags: Long,
    val activeTags: Long,
    val draftTags: Long,
    val emptyTags: Long,
    val coverageScore: Double,
    val overallHealth: Double,
    val tagsByStatus: List<StatusStat>
)

data class StatusStat(val status: String, val count: Long)

@Service
class KnowledgeGovernanceService(
    private val knowledgeTagRepo: KnowledgeTagRepository
) {
    fun getKnowledgeHealth(orgId: String): KnowledgeHealth {
        // 获取所有与该 org 相关的 workspace 的知识标签
        // 由于 KnowledgeTag 按 workspaceId 分组，这里获取全局模板标签统计
        val allTags = knowledgeTagRepo.findAll()

        val totalTags = allTags.size.toLong()
        val activeTags = allTags.count { it.status == "active" }.toLong()
        val draftTags = allTags.count { it.status == "draft" }.toLong()
        val emptyTags = allTags.count { it.status == "empty" || it.content.isBlank() }.toLong()

        val coverageScore = if (totalTags > 0) {
            ((activeTags + draftTags).toDouble() / totalTags.toDouble() * 100).coerceAtMost(100.0)
        } else 0.0

        val overallHealth = if (totalTags > 0) {
            (activeTags.toDouble() / totalTags.toDouble() * 100).coerceAtMost(100.0)
        } else 0.0

        val tagsByStatus = allTags
            .groupBy { it.status }
            .map { (status, tags) -> StatusStat(status, tags.size.toLong()) }

        return KnowledgeHealth(orgId, totalTags, activeTags, draftTags, emptyTags,
            coverageScore, overallHealth, tagsByStatus)
    }
}
