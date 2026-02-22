package com.forge.webide.repository

import com.forge.webide.entity.SkillUsageEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface SkillUsageRepository : JpaRepository<SkillUsageEntity, Long> {

    fun findBySkillName(skillName: String): List<SkillUsageEntity>

    fun findByCreatedAtAfter(since: Instant): List<SkillUsageEntity>

    fun findBySessionId(sessionId: String): List<SkillUsageEntity>

    @Query("SELECT u.skillName, COUNT(u) FROM SkillUsageEntity u WHERE u.createdAt > :since GROUP BY u.skillName ORDER BY COUNT(u) DESC")
    fun countBySkillNameSince(since: Instant): List<Array<Any>>

    @Query("SELECT COUNT(u) FROM SkillUsageEntity u WHERE u.skillName = :skillName")
    fun countBySkillName(skillName: String): Long

    @Query("SELECT COUNT(u) FROM SkillUsageEntity u WHERE u.skillName = :skillName AND u.success = true")
    fun countSuccessBySkillName(skillName: String): Long
}
