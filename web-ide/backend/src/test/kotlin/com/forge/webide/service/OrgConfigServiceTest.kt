package com.forge.webide.service

import com.forge.webide.entity.OrgDbConnectionEntity
import com.forge.webide.entity.OrgEnvConfigEntity
import com.forge.webide.entity.OrgModelConfigEntity
import com.forge.webide.model.*
import com.forge.webide.repository.OrgDbConnectionRepository
import com.forge.webide.repository.OrgEnvConfigRepository
import com.forge.webide.repository.OrgModelConfigRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class OrgConfigServiceTest {

    private lateinit var modelConfigRepository: OrgModelConfigRepository
    private lateinit var dbConnectionRepository: OrgDbConnectionRepository
    private lateinit var envConfigRepository: OrgEnvConfigRepository
    private lateinit var encryptionService: EncryptionService
    private lateinit var networkConfigService: NetworkConfigService
    private lateinit var service: OrgConfigService

    @BeforeEach
    fun setUp() {
        modelConfigRepository = mockk(relaxed = true)
        dbConnectionRepository = mockk(relaxed = true)
        envConfigRepository = mockk(relaxed = true)
        encryptionService = mockk(relaxed = true)
        networkConfigService = mockk(relaxed = true)
        service = OrgConfigService(modelConfigRepository, dbConnectionRepository, envConfigRepository, encryptionService, networkConfigService)
    }

    // =========================================================================
    // Model Config tests
    // =========================================================================

    @Test
    fun `listModelConfigs returns masked API keys`() {
        every { encryptionService.decrypt(any()) } returns "sk-anthropicLongKey1234"
        val entity = OrgModelConfigEntity(
            orgId = "org-1",
            provider = "anthropic",
            enabled = true,
            apiKeyEncrypted = "encrypted_value",
            updatedAt = Instant.now()
        )
        every { modelConfigRepository.findByOrgId("org-1") } returns listOf(entity)

        val result = service.listModelConfigs("org-1")
        assertThat(result).hasSize(1)
        assertThat(result[0].provider).isEqualTo("anthropic")
        assertThat(result[0].apiKeyMasked).isEqualTo("****1234")
        assertThat(result[0].enabled).isTrue()
    }

    @Test
    fun `listModelConfigs returns null apiKeyMasked when no key stored`() {
        val entity = OrgModelConfigEntity(
            orgId = "org-1",
            provider = "minimax",
            enabled = false,
            apiKeyEncrypted = null,
            updatedAt = Instant.now()
        )
        every { modelConfigRepository.findByOrgId("org-1") } returns listOf(entity)

        val result = service.listModelConfigs("org-1")
        assertThat(result[0].apiKeyMasked).isNull()
    }

    @Test
    fun `upsertModelConfig encrypts API key and saves`() {
        every { modelConfigRepository.findByOrgIdAndProvider("org-1", "anthropic") } returns null
        every { encryptionService.encrypt("sk-test-key") } returns "enc_sk-test-key"
        every { modelConfigRepository.save(any()) } answers { firstArg() }
        every { encryptionService.decrypt("enc_sk-test-key") } returns "sk-test-key"

        val req = UpsertModelConfigRequest(enabled = true, apiKey = "sk-test-key", baseUrl = null)
        val result = service.upsertModelConfig("org-1", "anthropic", req)

        assertThat(result.provider).isEqualTo("anthropic")
        assertThat(result.enabled).isTrue()
        verify { encryptionService.encrypt("sk-test-key") }
        verify { modelConfigRepository.save(any()) }
    }

    @Test
    fun `upsertModelConfig updates existing config`() {
        val existing = OrgModelConfigEntity(orgId = "org-1", provider = "anthropic", enabled = true, updatedAt = Instant.now())
        every { modelConfigRepository.findByOrgIdAndProvider("org-1", "anthropic") } returns existing
        every { modelConfigRepository.save(any()) } answers { firstArg() }

        val req = UpsertModelConfigRequest(enabled = false)
        service.upsertModelConfig("org-1", "anthropic", req)

        assertThat(existing.enabled).isFalse()
        verify { modelConfigRepository.save(existing) }
    }

    @Test
    fun `upsertModelConfig does not encrypt when apiKey is null`() {
        every { modelConfigRepository.findByOrgIdAndProvider("org-1", "gemini") } returns null
        every { modelConfigRepository.save(any()) } answers { firstArg() }

        service.upsertModelConfig("org-1", "gemini", UpsertModelConfigRequest(enabled = true, apiKey = null))

        verify(exactly = 0) { encryptionService.encrypt(any()) }
    }

    // =========================================================================
    // DB Connection tests
    // =========================================================================

    @Test
    fun `createDbConnection encrypts password and saves`() {
        every { encryptionService.encrypt("secret") } returns "enc_secret"
        every { dbConnectionRepository.save(any()) } answers { firstArg() }

        val req = CreateDbConnectionRequest(
            name = "Prod DB",
            jdbcUrl = "jdbc:postgresql://localhost:5432/mydb",
            username = "admin",
            password = "secret"
        )
        val result = service.createDbConnection("org-1", req)

        assertThat(result.name).isEqualTo("Prod DB")
        assertThat(result.username).isEqualTo("admin")
        verify { encryptionService.encrypt("secret") }
    }

    @Test
    fun `deleteDbConnection returns true when deleted`() {
        val entity = OrgDbConnectionEntity(id = "conn-1", orgId = "org-1", name = "DB", jdbcUrl = "jdbc:h2:mem:test")
        every { dbConnectionRepository.findById("conn-1") } returns Optional.of(entity)

        assertThat(service.deleteDbConnection("org-1", "conn-1")).isTrue()
        verify { dbConnectionRepository.deleteById("conn-1") }
    }

    @Test
    fun `deleteDbConnection returns false for wrong org`() {
        val entity = OrgDbConnectionEntity(id = "conn-1", orgId = "org-2", name = "DB", jdbcUrl = "jdbc:h2:mem:test")
        every { dbConnectionRepository.findById("conn-1") } returns Optional.of(entity)

        assertThat(service.deleteDbConnection("org-1", "conn-1")).isFalse()
    }

    @Test
    fun `testDbConnection returns error when not found`() {
        every { dbConnectionRepository.findById("missing") } returns Optional.empty()
        val result = service.testDbConnection("org-1", "missing")
        assertThat(result["success"]).isEqualTo(false)
    }

    @Test
    fun `testDbConnection returns error when connection fails`() {
        val entity = OrgDbConnectionEntity(
            id = "conn-1", orgId = "org-1", name = "Bad DB",
            jdbcUrl = "jdbc:postgresql://nonexistent:9999/db",
            username = "user", passwordEncrypted = "enc_pwd"
        )
        every { dbConnectionRepository.findById("conn-1") } returns Optional.of(entity)
        every { encryptionService.decrypt("enc_pwd") } returns "mypassword"

        val result = service.testDbConnection("org-1", "conn-1")
        assertThat(result["success"]).isEqualTo(false)
        assertThat(result["message"]).isNotNull()
    }

    // =========================================================================
    // Env Config tests
    // =========================================================================

    @Test
    fun `upsertEnvConfig encrypts sensitive values`() {
        every { envConfigRepository.findByOrgIdAndCategory("org-1", "build") } returns emptyList()
        every { encryptionService.encrypt("secret-val") } returns "enc_secret-val"
        every { envConfigRepository.save(any()) } answers { firstArg() }

        val req = UpsertEnvConfigRequest(configKey = "DEPLOY_TOKEN", configValue = "secret-val", isSensitive = true)
        val result = service.upsertEnvConfig("org-1", "build", req)

        assertThat(result.configValue).isEqualTo("****")
        assertThat(result.isSensitive).isTrue()
        verify { encryptionService.encrypt("secret-val") }
    }

    @Test
    fun `upsertEnvConfig stores plaintext for non-sensitive values`() {
        every { envConfigRepository.findByOrgIdAndCategory("org-1", "build") } returns emptyList()
        every { envConfigRepository.save(any()) } answers { firstArg() }

        val req = UpsertEnvConfigRequest(configKey = "NODE_VERSION", configValue = "20", isSensitive = false)
        val result = service.upsertEnvConfig("org-1", "build", req)

        assertThat(result.configValue).isEqualTo("20")
        verify(exactly = 0) { encryptionService.encrypt(any()) }
    }

    @Test
    fun `deleteEnvConfig returns true when deleted`() {
        val entity = OrgEnvConfigEntity(id = "ec-1", orgId = "org-1", category = "build", configKey = "JDK")
        every { envConfigRepository.findByOrgIdAndCategory("org-1", "build") } returns listOf(entity)

        assertThat(service.deleteEnvConfig("org-1", "build", "JDK")).isTrue()
        verify { envConfigRepository.deleteById("ec-1") }
    }

    @Test
    fun `deleteEnvConfig returns false when key not found`() {
        every { envConfigRepository.findByOrgIdAndCategory("org-1", "build") } returns emptyList()
        assertThat(service.deleteEnvConfig("org-1", "build", "MISSING")).isFalse()
    }

    @Test
    fun `listEnvConfigs filters by category when provided`() {
        val entities = listOf(
            OrgEnvConfigEntity(orgId = "org-1", category = "build", configKey = "JDK"),
            OrgEnvConfigEntity(orgId = "org-1", category = "build", configKey = "NODE")
        )
        every { envConfigRepository.findByOrgIdAndCategory("org-1", "build") } returns entities

        val result = service.listEnvConfigs("org-1", "build")
        assertThat(result).hasSize(2)
    }
}
