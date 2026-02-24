package com.forge.webide.repository

import com.forge.webide.entity.SkillQualityLearnedPatternEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SkillQualityLearnedPatternRepository : JpaRepository<SkillQualityLearnedPatternEntity, String> {

    fun findBySkillName(skillName: String): List<SkillQualityLearnedPatternEntity>

    fun findByStatus(status: String): List<SkillQualityLearnedPatternEntity>

    fun findBySkillNameAndStatus(skillName: String, status: String): List<SkillQualityLearnedPatternEntity>
}
