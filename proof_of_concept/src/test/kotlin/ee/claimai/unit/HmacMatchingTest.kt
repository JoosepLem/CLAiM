package ee.claimai.unit

import ee.claimai.encryption.DekCache
import ee.claimai.encryption.EncryptionService
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.crypto.spec.SecretKeySpec

class HmacMatchingTest {

    private val dek = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
    private val mockCache = mockk<DekCache>()
    private val service = EncryptionService(mockCache)

    @BeforeEach
    fun setUp() {
        every { mockCache.get(any()) } returns dek
    }

    @Test
    fun `TC4 same isikukood produces identical hmac hashes`() {
        val plaintext = "47101010033"
        val r1 = service.encrypt("tenant_a", plaintext)
        val r2 = service.encrypt("tenant_a", plaintext)
        assertThat(r1.hmac).isEqualTo(r2.hmac)
    }

    @Test
    fun `TC5 different isikukood produces different hmac hashes`() {
        val r1 = service.encrypt("tenant_a", "47101010033")
        val r2 = service.encrypt("tenant_a", "38001020044")
        assertThat(r1.hmac).isNotEqualTo(r2.hmac)
    }

    @Test
    fun `different tenants produce different hmac hashes for same isikukood`() {
        val plaintext = "47101010033"
        val tenantADek = SecretKeySpec(ByteArray(32) { it.toByte() }, "AES")
        val tenantBDek = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

        every { mockCache.get("tenant_a") } returns tenantADek
        val r1 = service.encrypt("tenant_a", plaintext)

        every { mockCache.get("tenant_b") } returns tenantBDek
        val r2 = service.encrypt("tenant_b", plaintext)

        assertThat(r1.hmac).isNotEqualTo(r2.hmac)
    }

    @Test
    fun `hmac hash is fixed length 32 bytes`() {
        val result = service.encrypt("tenant_a", "47101010033")
        assertThat(result.hmac.size).isEqualTo(32)
    }
}
