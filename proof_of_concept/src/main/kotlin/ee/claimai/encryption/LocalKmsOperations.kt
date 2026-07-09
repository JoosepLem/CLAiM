package ee.claimai.encryption

import org.slf4j.LoggerFactory
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class LocalKmsOperations(kek: SecretKey) : KmsOperations {

    private val kek: SecretKey = SecretKeySpec(kek.encoded, "AES")

    override fun encrypt(plaintext: ByteArray): ByteArray {
        val start = System.currentTimeMillis()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        cipher.init(Cipher.ENCRYPT_MODE, kek, GCMParameterSpec(128, iv))
        val ct = cipher.doFinal(plaintext)
        log.info("local_kms encrypt duration_us={}", (System.currentTimeMillis() - start) * 1000)
        return iv + ct
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        val start = System.currentTimeMillis()
        val iv = ciphertext.copyOfRange(0, 12)
        val ct = ciphertext.copyOfRange(12, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, kek, GCMParameterSpec(128, iv))
        val result = cipher.doFinal(ct)
        log.info("local_kms decrypt duration_us={}", (System.currentTimeMillis() - start) * 1000)
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(LocalKmsOperations::class.java)
    }
}
