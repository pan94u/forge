package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "process_flows")
class ProcessFlowEntity(
    @Id val id: String,
    @Column(name = "org_id") val orgId: String? = null,
    @Column(name = "workspace_id") val workspaceId: String? = null,
    @Column(name = "flow_name", nullable = false) val flowName: String,
    @Column(name = "flow_type") val flowType: String? = null,
    @Column(name = "raw_mermaid", nullable = false, columnDefinition = "TEXT") val rawMermaid: String,
    @Column(name = "parsed_nodes", columnDefinition = "TEXT") var parsedNodes: String? = null,
    @Column(name = "parsed_edges", columnDefinition = "TEXT") var parsedEdges: String? = null,
    @Column(name = "node_count") var nodeCount: Int? = null,
    @Column(name = "edge_count") var edgeCount: Int? = null,
    @Column(name = "extracted_at", nullable = false) val extractedAt: Instant = Instant.now(),
    @Column(name = "source_tag_id") val sourceTagId: String? = null
)
