package ee.claimai.encryption

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Service
class KeyManagementService(
    private val kms: KmsOperations,
    private val jdbcTemplate: JdbcTemplate
) {

    data class DekWithVersion(val keyVersion: Int, val rawDek: ByteArray)

    fun getOrCreateDek(tenantId: String): DekWithVersion {
        val existing = jdbcTemplate.query(
            "SELECT key_version, encrypted_dek FROM public.tenants WHERE tenant_id = ?",
            { rs, _ ->
                val version = rs.getInt("key_version")
                val encoded = rs.getBytes("encrypted_dek")
                if (encoded != null) {
                    val raw = kms.decrypt(encoded)
                    DekWithVersion(version, raw)
                } else {
                    null
                }
            },
            tenantId
        ).firstOrNull()

        if (existing != null) {
            log.info("dek_loaded tenant={} key_version={} source=kms_decrypt", tenantId, existing.keyVersion)
            return existing
        }

        val rawDek = ByteArray(32)
        SecureRandom().nextBytes(rawDek)
        val encryptedDek = kms.encrypt(rawDek)
        val keyVersion = 1

        jdbcTemplate.update(
            "UPDATE public.tenants SET encrypted_dek = ?, key_version = ? WHERE tenant_id = ?",
            encryptedDek, keyVersion, tenantId
        )

        log.info("dek_created tenant={} key_version={} source=kms_encrypt", tenantId, keyVersion)
        return DekWithVersion(keyVersion, rawDek)
    }

    companion object {
        private val log = LoggerFactory.getLogger(KeyManagementService::class.java)
    }
}
