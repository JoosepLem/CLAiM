package ee.claimai.encryption

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class EncryptionService(
    private val dekCache: DekCache
) {

    data class EncryptedData(val ciphertext: ByteArray, val hmac: ByteArray)

    fun encrypt(tenantId: String, plaintext: String): EncryptedData {
        val dek = dekCache.get(tenantId)
        val keys = Hkdf.deriveKeys(dek)
        val ciphertext = aesGcmEncrypt(plaintext, keys.encryptionKey)
        val hmac = computeHmac(plaintext, keys.hmacKey)
        return EncryptedData(ciphertext, hmac)
    }

    fun decrypt(tenantId: String, ciphertext: ByteArray, keyVersion: Int): String {
        val dek = dekCache.get(tenantId)
        val keys = Hkdf.deriveKeys(dek)
        return aesGcmDecrypt(ciphertext, keys.encryptionKey)
    }

    private fun aesGcmEncrypt(plaintext: String, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return iv + ct
    }

    private fun aesGcmDecrypt(data: ByteArray, key: SecretKey): String {
        val iv = data.copyOfRange(0, 12)
        val ct = data.copyOfRange(12, data.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun computeHmac(plaintext: String, key: SecretKey): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(plaintext.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private val log = LoggerFactory.getLogger(EncryptionService::class.java)
    }
}
