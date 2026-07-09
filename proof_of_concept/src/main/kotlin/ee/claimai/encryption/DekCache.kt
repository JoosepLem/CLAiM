package ee.claimai.encryption

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import javax.crypto.spec.SecretKeySpec

@Component
class DekCache(
    keyManagementService: KeyManagementService,
    ttl: Duration = Duration.ofMinutes(30)
) {

    private val cache: LoadingCache<String, Hkdf.DerivedKeys> = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .build { tenantId ->
            val dek = keyManagementService.getOrCreateDek(tenantId)
            Hkdf.deriveKeys(SecretKeySpec(dek.rawDek, "AES"))
        }

    fun get(tenantId: String): Hkdf.DerivedKeys {
        val cached = cache.getIfPresent(tenantId)
        if (cached != null) {
            log.debug("dek_cache hit tenant={}", tenantId)
            return cached
        }
        log.info("dek_cache miss tenant={} action=reload", tenantId)
        return cache.get(tenantId)
    }

    fun evict(tenantId: String) {
        cache.invalidate(tenantId)
        log.debug("Evicted DEK from cache for tenant {}", tenantId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(DekCache::class.java)
    }
}
