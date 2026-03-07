package com.forge.webide.service.governance

import com.forge.webide.repository.OrgMemberRepository
import com.forge.webide.repository.SkillUsageRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.temporal.ChronoUnit

data class TeamActivity(
    val orgId: String,
    val days: Int,
    val totalMembers: Long,
    val topSkills: List<SkillUsageStat>
)

data class SkillUsageStat(val skillName: String, val usageCount: Long)

@Service
class TeamGovernanceService(
    private val orgMemberRepo: OrgMemberRepository,
    private val skillUsageRepo: SkillUsageRepository
) {
    fun getTeamActivity(orgId: String, days: Int = 30): TeamActivity {
        val since = Instant.now().minus(days.toLong(), ChronoUnit.DAYS)
        val members = orgMemberRepo.findByOrgId(orgId)
        val totalMembers = members.size.toLong()

        val skillStats = skillUsageRepo.countBySkillNameSince(since)
            .map { row -> SkillUsageStat(row[0] as String, (row[1] as Number).toLong()) }
            .take(10)

        return TeamActivity(orgId, days, totalMembers, skillStats)
    }
}
