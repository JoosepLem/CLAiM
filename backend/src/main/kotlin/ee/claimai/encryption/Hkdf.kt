package ee.claimai.encryption

import java.io.ByteArrayOutputStream
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

object Hkdf {

    data class DerivedKeys(val encryptionKey: SecretKey, val hmacKey: SecretKey)

    fun deriveKeys(dek: SecretKey): DerivedKeys {
        val ikm = dek.encoded
        val salt = ByteArray(32)
        val prk = hkdfExtract(salt, ikm)
        val encBytes = hkdfExpand(prk, "enc".toByteArray(Charsets.UTF_8), 32)
        val hmacBytes = hkdfExpand(prk, "hmac".toByteArray(Charsets.UTF_8), 32)
        return DerivedKeys(
            encryptionKey = SecretKeySpec(encBytes, "AES"),
            hmacKey = SecretKeySpec(hmacBytes, "HmacSHA256")
        )
    }

    private fun hkdfExtract(salt: ByteArray, ikm: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        return mac.doFinal(ikm)
    }

    private fun hkdfExpand(prk: ByteArray, info: ByteArray, length: Int): ByteArray {
        val hashLen = 32
        val n = (length + hashLen - 1) / hashLen
        val out = ByteArrayOutputStream()
        var t = ByteArray(0)
        for (i in 1..n) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(i.toByte())
            t = mac.doFinal()
            out.write(t)
        }
        return out.toByteArray().copyOf(length)
    }
}
