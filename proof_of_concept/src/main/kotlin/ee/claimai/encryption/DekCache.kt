package ee.claimai.encryption

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Component
class DekCache(
    keyManagementService: KeyManagementService,
    ttl: Duration = Duration.ofMinutes(30)
) {

    private val cache: LoadingCache<String, KeyManagementService.DekWithVersion> = Caffeine.newBuilder()
        .expireAfterWrite(ttl)
        .build { tenantId -> keyManagementService.getOrCreateDek(tenantId) }

    fun get(tenantId: String): SecretKey {
        val cached = cache.getIfPresent(tenantId)
        if (cached != null) {
            log.debug("dek_cache hit tenant={}", tenantId)
            return SecretKeySpec(cached.rawDek, "AES")
        }
        log.info("dek_cache miss tenant={} action=reload", tenantId)
        val dek = cache.get(tenantId)
        return SecretKeySpec(dek.rawDek, "AES")
    }

    fun getWithVersion(tenantId: String): KeyManagementService.DekWithVersion {
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
