package com.forge.webide.service.governance

import com.forge.webide.entity.ProcessFlowEntity
import com.forge.webide.repository.KnowledgeTagRepository
import com.forge.webide.repository.ProcessFlowRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

data class ProcessFlowDto(
    val id: String,
    val orgId: String?,
    val workspaceId: String?,
    val flowName: String,
    val flowType: String?,
    val rawMermaid: String,
    val parsedNodes: String?,
    val parsedEdges: String?,
    val nodeCount: Int?,
    val edgeCount: Int?,
    val extractedAt: String,
    val sourceTagId: String?
)

@Service
class ProcessMiningService(
    private val processFlowRepo: ProcessFlowRepository,
    private val knowledgeTagRepo: KnowledgeTagRepository
) {
    private val logger = LoggerFactory.getLogger(ProcessMiningService::class.java)

    fun extractProcessFlows(orgId: String, workspaceId: String? = null): List<ProcessFlowDto> {
        // 查找 flow-diagrams 类型的知识标签
        val tags = if (workspaceId != null) {
            knowledgeTagRepo.findAll().filter { it.workspaceId == workspaceId && it.tagKey == "flow-diagrams" }
        } else {
            knowledgeTagRepo.findAll().filter { it.tagKey == "flow-diagrams" && it.content.isNotBlank() }
        }

        val extracted = mutableListOf<ProcessFlowEntity>()
        for (tag in tags) {
            val flows = extractMermaidFlows(tag.content, orgId, workspaceId, tag.id)
            extracted.addAll(flows)
        }

        if (extracted.isNotEmpty()) {
            processFlowRepo.saveAll(extracted)
            logger.info("Extracted ${extracted.size} process flows for org=$orgId")
        }

        return getProcessFlows(orgId, workspaceId)
    }

    fun getProcessFlows(orgId: String, workspaceId: String? = null): List<ProcessFlowDto> {
        val entities = if (workspaceId != null) {
            processFlowRepo.findByOrgIdAndWorkspaceIdOrderByExtractedAtDesc(orgId, workspaceId)
        } else {
            processFlowRepo.findByOrgIdOrderByExtractedAtDesc(orgId)
        }
        return entities.map { it.toDto() }
    }

    internal fun extractMermaidFlows(
        content: String,
        orgId: String,
        workspaceId: String?,
        sourceTagId: String
    ): List<ProcessFlowEntity> {
        val flows = mutableListOf<ProcessFlowEntity>()

        // 提取 mermaid 代码块
        val mermaidPattern = Regex("```mermaid\\s*\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)
        val matches = mermaidPattern.findAll(content)

        // 提取标题（## N. 标题）
        val titlePattern = Regex("## (\\d+)\\.\\s*(.+?)(?:\\n|$)")
        val titles = titlePattern.findAll(content).map { it.groupValues[2].trim() }.toList()

        matches.forEachIndexed { index, match ->
            val mermaidContent = match.groupValues[1].trim()
            val flowType = detectFlowType(mermaidContent)
            val flowName = if (index < titles.size) titles[index] else "流程图 ${index + 1}"
            val (nodes, edges) = parseMermaid(mermaidContent, flowType)

            flows.add(ProcessFlowEntity(
                id = UUID.randomUUID().toString(),
                orgId = orgId,
                workspaceId = workspaceId,
                flowName = flowName,
                flowType = flowType,
                rawMermaid = mermaidContent,
                parsedNodes = nodes,
                parsedEdges = edges,
                nodeCount = nodes.split(",").filter { it.isNotBlank() }.size,
                edgeCount = edges.split(",").filter { it.isNotBlank() }.size,
                sourceTagId = sourceTagId
            ))
        }

        return flows
    }

    internal fun detectFlowType(content: String): String {
        return when {
            content.startsWith("sequenceDiagram") -> "sequence"
            content.startsWith("stateDiagram") -> "state"
            content.startsWith("flowchart") || content.startsWith("graph") -> "flowchart"
            else -> "unknown"
        }
    }

    internal fun parseMermaid(content: String, flowType: String): Pair<String, String> {
        return when (flowType) {
            "flowchart", "unknown" -> parseFlowchart(content)
            "sequence" -> parseSequence(content)
            "state" -> parseState(content)
            else -> Pair("", "")
        }
    }

    private fun parseFlowchart(content: String): Pair<String, String> {
        val nodePattern = Regex("""(\w+)\[([^\]]+)\]""")
        val edgePattern = Regex("""(\w+)\s*--?>(?:[^\[>]*)?>?\s*(\w+)""")

        val nodes = nodePattern.findAll(content)
            .map { "${it.groupValues[1]}:${it.groupValues[2]}" }
            .distinct().joinToString(",")
        val edges = edgePattern.findAll(content)
            .map { "${it.groupValues[1]}->${it.groupValues[2]}" }
            .distinct().joinToString(",")

        return Pair(nodes, edges)
    }

    private fun parseSequence(content: String): Pair<String, String> {
        val participantPattern = Regex("""(?:participant|actor)\s+(\w+)(?:\s+as\s+(.+))?""")
        val messagePattern = Regex("""(\w+)\s*->>?\s*(\w+)\s*:(.*)""")

        val participants = participantPattern.findAll(content)
            .map { it.groupValues[2].ifBlank { it.groupValues[1] } }
            .distinct().joinToString(",")
        val messages = messagePattern.findAll(content)
            .map { "${it.groupValues[1]}->${it.groupValues[2]}" }
            .distinct().joinToString(",")

        return Pair(participants, messages)
    }

    private fun parseState(content: String): Pair<String, String> {
        val statePattern = Regex("""(\w+)\s*:\s*(.+)""")
        val transitionPattern = Regex("""(\w+)\s*-->\s*(\w+)""")

        val states = statePattern.findAll(content)
            .map { "${it.groupValues[1]}:${it.groupValues[2]}" }
            .distinct().joinToString(",")
        val transitions = transitionPattern.findAll(content)
            .map { "${it.groupValues[1]}->${it.groupValues[2]}" }
            .distinct().joinToString(",")

        return Pair(states, transitions)
    }

    private fun ProcessFlowEntity.toDto() = ProcessFlowDto(
        id = id, orgId = orgId, workspaceId = workspaceId,
        flowName = flowName, flowType = flowType, rawMermaid = rawMermaid,
        parsedNodes = parsedNodes, parsedEdges = parsedEdges,
        nodeCount = nodeCount, edgeCount = edgeCount,
        extractedAt = extractedAt.toString(), sourceTagId = sourceTagId
    )
}
