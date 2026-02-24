package com.forge.webide.service

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Base64

class EncryptionServiceTest {

    private fun generateKey(): String {
        val keyBytes = ByteArray(32)
        java.security.SecureRandom().nextBytes(keyBytes)
        return Base64.getEncoder().encodeToString(keyBytes)
    }

    @Test
    fun `encrypt and decrypt roundtrip`() {
        val key = generateKey()
        val service = EncryptionService(key)

        val plaintext = "sk-ant-api03-test-key-1234567890"
        val encrypted = service.encrypt(plaintext)

        assertThat(encrypted).isNotEqualTo(plaintext)
        assertThat(encrypted).isNotBlank()

        val decrypted = service.decrypt(encrypted)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `same plaintext produces different ciphertexts`() {
        val key = generateKey()
        val service = EncryptionService(key)

        val plaintext = "my-secret-key"
        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)

        assertThat(encrypted1).isNotEqualTo(encrypted2)

        // Both should decrypt back to the same plaintext
        assertThat(service.decrypt(encrypted1)).isEqualTo(plaintext)
        assertThat(service.decrypt(encrypted2)).isEqualTo(plaintext)
    }

    @Test
    fun `empty string returns empty`() {
        val key = generateKey()
        val service = EncryptionService(key)

        assertThat(service.encrypt("")).isEqualTo("")
        assertThat(service.decrypt("")).isEqualTo("")
    }

    @Test
    fun `blank string returns empty`() {
        val key = generateKey()
        val service = EncryptionService(key)

        assertThat(service.encrypt("   ")).isEqualTo("")
    }

    @Test
    fun `encrypt without key falls back to Base64 dev mode`() {
        val service = EncryptionService("")

        val encrypted = service.encrypt("my-api-key")
        assertThat(encrypted).startsWith("DEV:")
        assertThat(service.decrypt(encrypted)).isEqualTo("my-api-key")
    }

    @Test
    fun `decrypt non-dev ciphertext without key returns empty`() {
        val service = EncryptionService("")

        // Non-DEV: prefix data cannot be decrypted without key
        val result = service.decrypt("someCiphertext")
        assertThat(result).isEmpty()
    }

    @Test
    fun `isConfigured returns true when key is set`() {
        val key = generateKey()
        val service = EncryptionService(key)
        assertThat(service.isConfigured()).isTrue()
    }

    @Test
    fun `isConfigured returns false when key is empty`() {
        val service = EncryptionService("")
        assertThat(service.isConfigured()).isFalse()
    }

    @Test
    fun `decrypt with wrong key throws`() {
        val key1 = generateKey()
        val key2 = generateKey()
        val service1 = EncryptionService(key1)
        val service2 = EncryptionService(key2)

        val encrypted = service1.encrypt("secret")

        assertThatThrownBy { service2.decrypt(encrypted) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("解密失败")
    }

    @Test
    fun `handles unicode content`() {
        val key = generateKey()
        val service = EncryptionService(key)

        val plaintext = "密钥测试-api-key-日本語"
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)

        assertThat(decrypted).isEqualTo(plaintext)
    }
}
