package com.forge.webide.service

import com.forge.webide.entity.UserModelConfigEntity
import com.forge.webide.repository.UserModelConfigRepository
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64

class UserModelConfigServiceTest {

    private lateinit var repository: UserModelConfigRepository
    private lateinit var encryptionService: EncryptionService
    private lateinit var service: UserModelConfigService

    private fun generateKey(): String {
        val keyBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(keyBytes)
        return Base64.getEncoder().encodeToString(keyBytes)
    }

    @BeforeEach
    fun setUp() {
        repository = mockk(relaxed = true)
        encryptionService = EncryptionService(generateKey())
        service = UserModelConfigService(repository, encryptionService)
    }

    @Test
    fun `getUserConfigs returns masked configs`() {
        val encrypted = encryptionService.encrypt("sk-ant-api03-test-key-1234567890")
        val entity = UserModelConfigEntity(
            id = "1",
            userId = "user1",
            provider = "anthropic",
            apiKeyEncrypted = encrypted,
            baseUrl = "",
            enabled = true
        )
        every { repository.findByUserId("user1") } returns listOf(entity)

        val configs = service.getUserConfigs("user1")

        assertThat(configs).hasSize(1)
        assertThat(configs[0].provider).isEqualTo("anthropic")
        assertThat(configs[0].hasApiKey).isTrue()
        assertThat(configs[0].apiKeyMasked).contains("****")
        // Should show first 4 and last 4 chars
        assertThat(configs[0].apiKeyMasked).startsWith("sk-a")
        assertThat(configs[0].apiKeyMasked).endsWith("7890")
    }

    @Test
    fun `saveUserConfig creates new entity`() {
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns null
        every { repository.save(any()) } answers { firstArg() }

        val request = UserModelConfigRequest(
            provider = "anthropic",
            apiKey = "sk-test-key",
            baseUrl = "",
            enabled = true
        )

        val result = service.saveUserConfig("user1", request)

        assertThat(result.provider).isEqualTo("anthropic")
        assertThat(result.hasApiKey).isTrue()

        verify { repository.save(match { it.userId == "user1" && it.provider == "anthropic" }) }
    }

    @Test
    fun `saveUserConfig updates existing entity`() {
        val existingEncrypted = encryptionService.encrypt("old-key")
        val existing = UserModelConfigEntity(
            id = "1",
            userId = "user1",
            provider = "anthropic",
            apiKeyEncrypted = existingEncrypted,
            enabled = true
        )
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns existing
        every { repository.save(any()) } answers { firstArg() }

        val request = UserModelConfigRequest(
            provider = "anthropic",
            apiKey = "new-key",
            enabled = true
        )

        service.saveUserConfig("user1", request)

        verify {
            repository.save(match {
                it.id == "1" && encryptionService.decrypt(it.apiKeyEncrypted) == "new-key"
            })
        }
    }

    @Test
    fun `saveUserConfig with empty apiKey keeps existing key`() {
        val existingEncrypted = encryptionService.encrypt("existing-key")
        val existing = UserModelConfigEntity(
            id = "1",
            userId = "user1",
            provider = "anthropic",
            apiKeyEncrypted = existingEncrypted,
            enabled = true
        )
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns existing
        every { repository.save(any()) } answers { firstArg() }

        val request = UserModelConfigRequest(
            provider = "anthropic",
            apiKey = "",
            enabled = false
        )

        service.saveUserConfig("user1", request)

        verify {
            repository.save(match {
                it.apiKeyEncrypted == existingEncrypted && !it.enabled
            })
        }
    }

    @Test
    fun `deleteUserConfig calls repository`() {
        service.deleteUserConfig("user1", "anthropic")
        verify { repository.deleteByUserIdAndProvider("user1", "anthropic") }
    }

    @Test
    fun `getDecryptedConfig returns decrypted key`() {
        val encrypted = encryptionService.encrypt("my-secret-key")
        val entity = UserModelConfigEntity(
            id = "1",
            userId = "user1",
            provider = "anthropic",
            apiKeyEncrypted = encrypted,
            enabled = true
        )
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns entity

        val config = service.getDecryptedConfig("user1", "anthropic")
        assertThat(config).isNotNull()
        assertThat(config!!.apiKey).isEqualTo("my-secret-key")
    }

    @Test
    fun `getDecryptedConfig returns null when not found`() {
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns null
        assertThat(service.getDecryptedConfig("user1", "anthropic")).isNull()
    }

    @Test
    fun `getDecryptedConfig returns null when disabled`() {
        val entity = UserModelConfigEntity(
            id = "1",
            userId = "user1",
            provider = "anthropic",
            apiKeyEncrypted = encryptionService.encrypt("key"),
            enabled = false
        )
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns entity
        assertThat(service.getDecryptedConfig("user1", "anthropic")).isNull()
    }

    @Test
    fun `getUserConfig returns null when not found`() {
        every { repository.findByUserIdAndProvider("user1", "anthropic") } returns null
        assertThat(service.getUserConfig("user1", "anthropic")).isNull()
    }
}
