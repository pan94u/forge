package com.forge.webide.repository

import com.forge.webide.entity.SkillPreferenceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SkillPreferenceRepository : JpaRepository<SkillPreferenceEntity, Long> {

    fun findByWorkspaceId(workspaceId: String): List<SkillPreferenceEntity>

    fun findByWorkspaceIdAndSkillName(workspaceId: String, skillName: String): SkillPreferenceEntity?

    fun deleteByWorkspaceIdAndSkillName(workspaceId: String, skillName: String)
}
