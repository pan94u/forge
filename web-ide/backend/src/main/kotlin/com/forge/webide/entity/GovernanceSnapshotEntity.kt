package com.forge.webide.entity

import jakarta.persistence.*
import java.time.Instant

@Entity
@Table(name = "governance_snapshots")
class GovernanceSnapshotEntity(
    @Id val id: String,
    @Column(name = "org_id", nullable = false) val orgId: String,
    @Column(nullable = false, length = 50) val domain: String,
    @Column(name = "snapshot_data", nullable = false, columnDefinition = "TEXT") var snapshotData: String,
    @Column(name = "period_start", nullable = false) val periodStart: Instant,
    @Column(name = "period_end", nullable = false) val periodEnd: Instant,
    @Column(name = "created_at", nullable = false) val createdAt: Instant = Instant.now()
)
