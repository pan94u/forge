package com.forge.webide.repository

import com.forge.webide.entity.OrgMemberEntity
import com.forge.webide.entity.OrgMemberId
import org.springframework.data.jpa.repository.JpaRepository

interface OrgMemberRepository : JpaRepository<OrgMemberEntity, OrgMemberId> {
    fun findByOrgId(orgId: String): List<OrgMemberEntity>
    fun findByUserId(userId: String): List<OrgMemberEntity>
}
