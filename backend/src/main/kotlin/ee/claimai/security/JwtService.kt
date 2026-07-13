package ee.claimai.security

import ee.claimai.config.JwtProperties
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(jwtProperties: JwtProperties) {
    private val log = LoggerFactory.getLogger(JwtService::class.java)
    private val key: SecretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
    private val expirationHours: Long = jwtProperties.expirationHours

    fun generateToken(username: String, tenantId: String?, role: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationHours * 3600 * 1000)

        val builder = Jwts.builder()
            .subject(username)
            .claim("authorities", listOf("ROLE_$role"))
            .issuer("claim-poc")
            .issuedAt(now)
            .expiration(expiry)

        if (tenantId != null) {
            builder.claim("tenant_id", tenantId)
        }

        return builder.signWith(key).compact()
    }

    fun validateAndExtract(token: String): Map<String, Any>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            val authorities = claims["authorities"] as? List<*> ?: emptyList<Any>()

            val result = mutableMapOf<String, Any>(
                "sub" to (claims.subject ?: return null),
                "iss" to (claims.issuer ?: return null),
                "authorities" to authorities.filterIsInstance<String>()
            )

            val tenantId = claims["tenant_id"] as? String
            if (tenantId != null) {
                result["tenant_id"] = tenantId
            }

            result
        } catch (e: Exception) {
            log.warn("JWT validation failed: {}", e.message)
            null
        }
    }
}
