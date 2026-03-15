package com.forge.eval.api.repository

import com.forge.eval.api.entity.*
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface EvalSuiteRepository : JpaRepository<EvalSuiteEntity, UUID> {
    fun findByPlatform(platform: PlatformEnum, pageable: Pageable): Page<EvalSuiteEntity>
    fun findByPlatformAndAgentType(
        platform: PlatformEnum,
        agentType: AgentTypeEnum,
        pageable: Pageable
    ): Page<EvalSuiteEntity>
}

@Repository
interface EvalTaskRepository : JpaRepository<EvalTaskEntity, UUID> {
    fun findBySuiteId(suiteId: UUID): List<EvalTaskEntity>
    fun findBySuiteIdIn(suiteIds: List<UUID>): List<EvalTaskEntity>
    fun countBySuiteId(suiteId: UUID): Long
}

@Repository
interface EvalRunRepository : JpaRepository<EvalRunEntity, UUID> {
    fun findBySuiteId(suiteId: UUID, pageable: Pageable): Page<EvalRunEntity>
}

@Repository
interface EvalTrialRepository : JpaRepository<EvalTrialEntity, UUID> {
    fun findByRunId(runId: UUID): List<EvalTrialEntity>
    fun findByTaskId(taskId: UUID): List<EvalTrialEntity>
}

@Repository
interface EvalTranscriptRepository : JpaRepository<EvalTranscriptEntity, UUID> {
    fun findByTrialId(trialId: UUID): EvalTranscriptEntity?
}

@Repository
interface EvalGradeRepository : JpaRepository<EvalGradeEntity, UUID> {
    fun findByTrialId(trialId: UUID): List<EvalGradeEntity>
    fun findByTrialIdIn(trialIds: List<UUID>): List<EvalGradeEntity>
}
