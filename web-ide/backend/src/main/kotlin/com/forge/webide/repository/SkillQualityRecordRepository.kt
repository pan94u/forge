package com.forge.webide.repository

import com.forge.webide.entity.SkillQualityRecordEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface SkillQualityRecordRepository : JpaRepository<SkillQualityRecordEntity, String> {

    fun findBySkillName(skillName: String): List<SkillQualityRecordEntity>

    fun findBySkillNameOrderByCreatedAtDesc(skillName: String): List<SkillQualityRecordEntity>

    fun findByCreatedAtAfter(since: Instant): List<SkillQualityRecordEntity>

    @Query("SELECT COUNT(r) FROM SkillQualityRecordEntity r WHERE r.skillName = :skillName")
    fun countBySkillName(skillName: String): Long

    @Query("SELECT COUNT(r) FROM SkillQualityRecordEntity r WHERE r.skillName = :skillName AND r.overallStatus = 'PASSED'")
    fun countPassedBySkillName(skillName: String): Long

    @Query("SELECT COUNT(r) FROM SkillQualityRecordEntity r WHERE r.skillName = :skillName AND r.overallStatus = 'FAILED'")
    fun countFailedBySkillName(skillName: String): Long

    @Query("""
        SELECT r.skillName, r.overallStatus, COUNT(r)
        FROM SkillQualityRecordEntity r
        WHERE r.createdAt > :since
        GROUP BY r.skillName, r.overallStatus
        ORDER BY r.skillName
    """)
    fun countBySkillNameAndStatusSince(since: Instant): List<Array<Any>>
}
