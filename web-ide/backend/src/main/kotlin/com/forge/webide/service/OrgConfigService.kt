package com.forge.webide.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.forge.webide.entity.OrgDbConnectionEntity
import com.forge.webide.entity.OrgEnvConfigEntity
import com.forge.webide.entity.OrgModelConfigEntity
import com.forge.webide.model.*
import com.forge.webide.repository.OrgDbConnectionRepository
import com.forge.webide.repository.OrgEnvConfigRepository
import com.forge.webide.repository.OrgModelConfigRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.sql.DriverManager
import java.time.Instant

@Service
class OrgConfigService(
    private val modelConfigRepository: OrgModelConfigRepository,
    private val dbConnectionRepository: OrgDbConnectionRepository,
    private val envConfigRepository: OrgEnvConfigRepository,
    private val encryptionService: EncryptionService
) {

    private val logger = LoggerFactory.getLogger(OrgConfigService::class.java)
    private val objectMapper = jacksonObjectMapper()

    // =========================================================================
    // Model Config
    // =========================================================================

    fun listModelConfigs(orgId: String): List<OrgModelConfig> {
        return modelConfigRepository.findByOrgId(orgId).map { it.toModel() }
    }

    @Transactional
    fun upsertModelConfig(orgId: String, provider: String, req: UpsertModelConfigRequest): OrgModelConfig {
        val existing = modelConfigRepository.findByOrgIdAndProvider(orgId, provider)
        val entity = existing ?: OrgModelConfigEntity(orgId = orgId, provider = provider)

        entity.enabled = req.enabled
        if (req.apiKey != null) {
            entity.apiKeyEncrypted = encryptionService.encrypt(req.apiKey)
        }
        req.baseUrl?.let { entity.baseUrl = it }
        entity.modelAllowlistJson = req.modelAllowlist?.let { objectMapper.writeValueAsString(it) }
        entity.updatedAt = Instant.now()

        modelConfigRepository.save(entity)
        logger.info("Upserted model config for org {} provider {}", orgId, provider)
        return entity.toModel()
    }

    // =========================================================================
    // DB Connections
    // =========================================================================

    fun listDbConnections(orgId: String): List<OrgDbConnection> {
        return dbConnectionRepository.findByOrgId(orgId).map { it.toModel() }
    }

    @Transactional
    fun createDbConnection(orgId: String, req: CreateDbConnectionRequest): OrgDbConnection {
        val entity = OrgDbConnectionEntity(
            orgId = orgId,
            name = req.name,
            jdbcUrl = req.jdbcUrl,
            username = req.username,
            passwordEncrypted = req.password?.let { encryptionService.encrypt(it) },
            accessLevel = req.accessLevel
        )
        dbConnectionRepository.save(entity)
        logger.info("Created DB connection '{}' for org {}", req.name, orgId)
        return entity.toModel()
    }

    @Transactional
    fun deleteDbConnection(orgId: String, id: String): Boolean {
        val entity = dbConnectionRepository.findById(id).orElse(null) ?: return false
        if (entity.orgId != orgId) return false
        dbConnectionRepository.deleteById(id)
        logger.info("Deleted DB connection {} for org {}", id, orgId)
        return true
    }

    fun testDbConnection(orgId: String, id: String): Map<String, Any> {
        val entity = dbConnectionRepository.findById(id).orElse(null)
            ?: return mapOf("success" to false, "message" to "Connection not found")
        if (entity.orgId != orgId) return mapOf("success" to false, "message" to "Connection not found")

        return try {
            val password = entity.passwordEncrypted?.let { encryptionService.decrypt(it) }
            val conn = if (password != null) {
                DriverManager.getConnection(entity.jdbcUrl, entity.username, password)
            } else {
                DriverManager.getConnection(entity.jdbcUrl)
            }
            conn.close()
            mapOf("success" to true, "message" to "Connection successful")
        } catch (e: Exception) {
            logger.warn("DB connection test failed for {}: {}", id, e.message)
            mapOf("success" to false, "message" to (e.message ?: "Connection failed"))
        }
    }

    // =========================================================================
    // Env Configs
    // =========================================================================

    fun listEnvConfigs(orgId: String, category: String? = null): List<OrgEnvConfig> {
        val entities = if (category.isNullOrBlank()) {
            envConfigRepository.findByOrgId(orgId)
        } else {
            envConfigRepository.findByOrgIdAndCategory(orgId, category)
        }
        return entities.map { it.toModel() }
    }

    @Transactional
    fun upsertEnvConfig(orgId: String, category: String, req: UpsertEnvConfigRequest): OrgEnvConfig {
        val existing = envConfigRepository.findByOrgIdAndCategory(orgId, category)
            .firstOrNull { it.configKey == req.configKey }

        val entity = existing ?: OrgEnvConfigEntity(orgId = orgId, category = category, configKey = req.configKey)

        entity.configKey = req.configKey
        entity.isSensitive = req.isSensitive
        entity.description = req.description
        entity.configValue = if (req.isSensitive && req.configValue != null) {
            encryptionService.encrypt(req.configValue)
        } else {
            req.configValue
        }

        envConfigRepository.save(entity)
        logger.info("Upserted env config {}/{} for org {}", category, req.configKey, orgId)
        return entity.toModel()
    }

    @Transactional
    fun deleteEnvConfig(orgId: String, category: String, key: String): Boolean {
        val existing = envConfigRepository.findByOrgIdAndCategory(orgId, category)
            .firstOrNull { it.configKey == key } ?: return false
        envConfigRepository.deleteById(existing.id)
        logger.info("Deleted env config {}/{} for org {}", category, key, orgId)
        return true
    }

    // =========================================================================
    // Mapping helpers
    // =========================================================================

    private fun OrgModelConfigEntity.toModel(): OrgModelConfig {
        val allowlist: List<String>? = modelAllowlistJson?.let {
            try { objectMapper.readValue(it) } catch (e: Exception) { null }
        }
        val masked = apiKeyEncrypted?.takeIf { it.isNotBlank() }?.let {
            try {
                val decrypted = encryptionService.decrypt(it)
                if (decrypted.length > 4) "****" + decrypted.takeLast(4) else "****"
            } catch (e: Exception) { "****" }
        }
        return OrgModelConfig(
            id = id,
            orgId = orgId,
            provider = provider,
            enabled = enabled,
            apiKeyMasked = masked,
            baseUrl = baseUrl,
            modelAllowlist = allowlist,
            updatedAt = updatedAt
        )
    }

    private fun OrgDbConnectionEntity.toModel() = OrgDbConnection(
        id = id,
        orgId = orgId,
        name = name,
        jdbcUrl = jdbcUrl,
        username = username,
        accessLevel = accessLevel,
        createdAt = createdAt
    )

    private fun OrgEnvConfigEntity.toModel(): OrgEnvConfig {
        val displayValue = if (isSensitive && configValue != null) {
            "****"
        } else {
            configValue
        }
        return OrgEnvConfig(
            id = id,
            orgId = orgId,
            category = category,
            configKey = configKey,
            configValue = displayValue,
            isSensitive = isSensitive,
            description = description
        )
    }
}
