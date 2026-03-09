package com.forge.webide.service.governance

import com.forge.webide.repository.ProcessFlowRepository
import org.springframework.stereotype.Service

data class ProcessSummary(
    val orgId: String,
    val totalFlows: Int,
    val totalNodes: Int,
    val totalEdges: Int,
    val flowsByType: Map<String, Int>,
    val avgNodesPerFlow: Double,
    val recentFlows: List<ProcessFlowSummaryItem>
)

data class ProcessFlowSummaryItem(
    val id: String,
    val flowName: String,
    val flowType: String,
    val nodeCount: Int,
    val edgeCount: Int,
    val extractedAt: String
)

@Service
class ProcessSummaryService(
    private val processFlowRepo: ProcessFlowRepository
) {
    fun getProcessSummary(orgId: String): ProcessSummary {
        val flows = processFlowRepo.findByOrgIdOrderByExtractedAtDesc(orgId)
        val totalFlows = flows.size
        val totalNodes = flows.sumOf { it.nodeCount ?: 0 }
        val totalEdges = flows.sumOf { it.edgeCount ?: 0 }

        val flowsByType = flows
            .groupBy { it.flowType ?: "unknown" }
            .mapValues { it.value.size }

        val avgNodesPerFlow = if (totalFlows > 0) {
            totalNodes.toDouble() / totalFlows.toDouble()
        } else 0.0

        val recentFlows = flows.take(10).map { flow ->
            ProcessFlowSummaryItem(
                id = flow.id,
                flowName = flow.flowName,
                flowType = flow.flowType ?: "unknown",
                nodeCount = flow.nodeCount ?: 0,
                edgeCount = flow.edgeCount ?: 0,
                extractedAt = flow.extractedAt.toString()
            )
        }

        return ProcessSummary(
            orgId = orgId,
            totalFlows = totalFlows,
            totalNodes = totalNodes,
            totalEdges = totalEdges,
            flowsByType = flowsByType,
            avgNodesPerFlow = avgNodesPerFlow,
            recentFlows = recentFlows
        )
    }
}
