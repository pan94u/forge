package com.forge.webide.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM 加密服务，用于安全存储用户 API Key。
 *
 * 加密格式：Base64(iv + ciphertext + authTag)
 * - IV: 12 字节随机数
 * - Auth Tag: 128 bit
 * - 每次加密使用新的随机 IV，确保相同明文产生不同密文
 *
 * 加密密钥通过环境变量 `FORGE_ENCRYPTION_KEY` 注入，
 * 必须是 32 字节（256 位）的 Base64 编码字符串。
 */
@Service
class EncryptionService(
    @Value("\${forge.encryption.key:}") private val encryptionKeyBase64: String
) {

    private val logger = LoggerFactory.getLogger(EncryptionService::class.java)
    private val secureRandom = SecureRandom()

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val IV_LENGTH = 12
        private const val TAG_LENGTH_BITS = 128
        private const val KEY_LENGTH_BYTES = 32
    }

    private val secretKey: SecretKey? by lazy {
        if (encryptionKeyBase64.isBlank()) {
            logger.warn("FORGE_ENCRYPTION_KEY 未设置，API Key 加密不可用")
            null
        } else {
            try {
                val keyBytes = Base64.getDecoder().decode(encryptionKeyBase64)
                require(keyBytes.size == KEY_LENGTH_BYTES) {
                    "加密密钥必须是 $KEY_LENGTH_BYTES 字节（当前 ${keyBytes.size} 字节）"
                }
                SecretKeySpec(keyBytes, "AES")
            } catch (e: Exception) {
                logger.error("FORGE_ENCRYPTION_KEY 格式无效: {}", e.message)
                null
            }
        }
    }

    /**
     * 加密明文字符串。
     * 如果加密密钥未配置，抛出异常。
     */
    fun encrypt(plaintext: String): String {
        if (plaintext.isBlank()) return ""
        val key = secretKey ?: throw IllegalStateException("Encryption key is not configured. Please set FORGE_ENCRYPTION_KEY environment variable.")

        val iv = ByteArray(IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // iv + ciphertext (includes auth tag)
        val combined = iv + ciphertext
        return Base64.getEncoder().encodeToString(combined)
    }

    /**
     * 解密密文字符串。
     * 如果加密密钥未配置，抛出异常。
     */
    fun decrypt(ciphertext: String): String {
        if (ciphertext.isBlank()) return ""
        val key = secretKey ?: throw IllegalStateException("Encryption key is not configured. Please set FORGE_ENCRYPTION_KEY environment variable.")

        return try {
            val combined = Base64.getDecoder().decode(ciphertext)
            require(combined.size > IV_LENGTH) { "密文数据太短" }

            val iv = combined.copyOfRange(0, IV_LENGTH)
            val encrypted = combined.copyOfRange(IV_LENGTH, combined.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_LENGTH_BITS, iv))

            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error("解密失败: {}", e.message)
            throw IllegalStateException("API Key 解密失败，请检查加密密钥配置", e)
        }
    }

    /**
     * 检查加密服务是否已配置密钥。
     */
    fun isConfigured(): Boolean = secretKey != null
}
