package com.forge.webide.repository

import com.forge.webide.entity.EvalResultEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface EvalResultRepository : JpaRepository<EvalResultEntity, String> {

    fun findByRunIdOrderByCreatedAtAsc(runId: String): List<EvalResultEntity>

    fun findByRunIdAndTaskId(runId: String, taskId: String): List<EvalResultEntity>

    fun countByRunIdAndStatus(runId: String, status: String): Long

    @Query("SELECT r.status, COUNT(r) FROM EvalResultEntity r WHERE r.runId = :runId GROUP BY r.status")
    fun countByStatusForRun(runId: String): List<Array<Any>>
}
