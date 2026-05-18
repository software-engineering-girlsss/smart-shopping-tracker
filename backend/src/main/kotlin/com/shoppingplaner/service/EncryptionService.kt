package com.shoppingplaner.service

import com.shoppingplaner.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService(props: AppProperties) {

    private val log = LoggerFactory.getLogger(EncryptionService::class.java)

    // Null when APP_ENCRYPTION_KEY is not configured — app still starts, Picnic credential
    // storage is disabled and operations that require encryption fail at request time.
    private val key: SecretKeySpec? = run {
        val raw = props.encryptionKey
        if (raw.isBlank()) {
            log.error(
                "APP_ENCRYPTION_KEY is not set — Picnic credential storage is disabled. " +
                "Generate a key with: openssl rand -base64 32 " +
                "and set it in the Render dashboard / docker-compose environment."
            )
            null
        } else {
            runCatching { SecretKeySpec(Base64.getDecoder().decode(raw), "AES") }
                .onFailure { log.error("APP_ENCRYPTION_KEY is invalid (not a valid base64 AES-256 key): {}", it.message) }
                .getOrNull()
        }
    }

    /** Encrypts [plaintext] and returns `base64(iv):base64(ciphertext)`. */
    fun encrypt(plaintext: String): String {
        val k = checkNotNull(key) {
            "APP_ENCRYPTION_KEY is not configured — cannot store Picnic credentials. Set the variable and redeploy."
        }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, k, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val enc = Base64.getEncoder()
        return "${enc.encodeToString(iv)}:${enc.encodeToString(ciphertext)}"
    }

    /**
     * Decrypts a value produced by [encrypt].
     * Returns null (and logs a warning) if the value is malformed, decryption fails, or the key is not set.
     */
    fun decrypt(encoded: String): String? {
        val k = key ?: return null
        val parts = encoded.split(":")
        if (parts.size != 2) {
            log.warn("decrypt: unexpected format (missing ':' separator) — value may be legacy plaintext")
            return null
        }
        return runCatching {
            val dec = Base64.getDecoder()
            val iv = dec.decode(parts[0])
            val ciphertext = dec.decode(parts[1])
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.onFailure { log.warn("decrypt: AES/GCM decryption failed: {}", it.message) }.getOrNull()
    }
}
