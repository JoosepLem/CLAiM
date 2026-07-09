package ee.claimai.unit

import ee.claimai.encryption.DekCache
import ee.claimai.encryption.Hkdf
import ee.claimai.encryption.KeyManagementService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

class DekCacheEvictionTest {

    private val mockKms = mockk<KeyManagementService>()

    @Test
    fun `DEK is loaded from KMS on first access and cached for subsequent calls`() {
        val rawDek = ByteArray(32) { it.toByte() }
        every { mockKms.getOrCreateDek("tenant_a") } returns KeyManagementService.DekWithVersion(1, rawDek)

        val cache = DekCache(mockKms, Duration.ofHours(1))

        val first = cache.get("tenant_a")
        val second = cache.get("tenant_a")

        val expected = Hkdf.deriveKeys(SecretKeySpec(rawDek, "AES"))
        assertThat(first.encryptionKey.encoded).isEqualTo(expected.encryptionKey.encoded)
        assertThat(second.encryptionKey.encoded).isEqualTo(expected.encryptionKey.encoded)
        verify(exactly = 1) { mockKms.getOrCreateDek("tenant_a") }
    }

    @Test
    fun `after eviction DEK is reloaded from KMS on next access`() {
        val rawDek1 = ByteArray(32) { it.toByte() }
        val rawDek2 = ByteArray(32) { (it + 1).toByte() }
        every { mockKms.getOrCreateDek("tenant_a") } returns KeyManagementService.DekWithVersion(1, rawDek1) andThen KeyManagementService.DekWithVersion(2, rawDek2)

        val cache = DekCache(mockKms, Duration.ofHours(1))

        val first = cache.get("tenant_a")
        cache.evict("tenant_a")
        val second = cache.get("tenant_a")

        val expected1 = Hkdf.deriveKeys(SecretKeySpec(rawDek1, "AES"))
        val expected2 = Hkdf.deriveKeys(SecretKeySpec(rawDek2, "AES"))
        assertThat(first.encryptionKey.encoded).isEqualTo(expected1.encryptionKey.encoded)
        assertThat(second.encryptionKey.encoded).isEqualTo(expected2.encryptionKey.encoded)
        verify(exactly = 2) { mockKms.getOrCreateDek("tenant_a") }
    }

    @Test
    fun `evicting a non-existent tenant does not throw`() {
        every { mockKms.getOrCreateDek("tenant_a") } returns KeyManagementService.DekWithVersion(1, ByteArray(32) { it.toByte() })

        val cache = DekCache(mockKms, Duration.ofHours(1))
        cache.evict("nonexistent")

        val key = cache.get("tenant_a")
        assertThat(key).isNotNull
    }
}
