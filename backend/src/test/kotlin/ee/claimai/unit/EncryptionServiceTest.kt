package ee.claimai.unit

import ee.claimai.encryption.DekCache
import ee.claimai.encryption.EncryptionService
import ee.claimai.encryption.Hkdf
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class EncryptionServiceTest {

    private val dek = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val derivedKeys = Hkdf.deriveKeys(dek)
    private val mockCache = mockk<DekCache>()
    private val service = EncryptionService(mockCache)

    @BeforeEach
    fun setUp() {
        every { mockCache.get(any()) } returns derivedKeys
    }

    @Test
    fun `TC2 AES-256-GCM round trip encrypt then decrypt returns original plaintext`() {
        val plaintext = "47101010033"
        val result = service.encrypt("tenant_a", plaintext)
        val decrypted = service.decrypt("tenant_a", result.ciphertext, 1)
        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `TC3 encrypt same isikukood twice produces different ciphertexts due to random nonce`() {
        val plaintext = "47101010033"
        val r1 = service.encrypt("tenant_a", plaintext)
        val r2 = service.encrypt("tenant_a", plaintext)
        assertThat(r1.ciphertext).isNotEqualTo(r2.ciphertext)
        assertThat(service.decrypt("tenant_a", r1.ciphertext, 1)).isEqualTo(plaintext)
        assertThat(service.decrypt("tenant_a", r2.ciphertext, 1)).isEqualTo(plaintext)
    }

    @Test
    fun `TC6 wrong DEK cannot decrypt due to GCM authentication tag mismatch`() {
        val data = service.encrypt("tenant_a", "47101010033")
        val wrongDek = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")
        every { mockCache.get("tenant_a") } returns Hkdf.deriveKeys(wrongDek)

        assertThatThrownBy {
            service.decrypt("tenant_a", data.ciphertext, 1)
        }.isInstanceOf(AEADBadTagException::class.java)
    }

    @Test
    fun `encrypt and decrypt with unicode characters works correctly`() {
        val plaintext = "õäöüšž47101010033"
        val result = service.encrypt("tenant_a", plaintext)
        val decrypted = service.decrypt("tenant_a", result.ciphertext, 1)
        assertThat(decrypted).isEqualTo(plaintext)
    }
}
