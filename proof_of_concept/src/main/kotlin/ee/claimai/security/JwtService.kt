package ee.claimai.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Date
import javax.crypto.SecretKey

@Service
class JwtService(
    @Value("\${jwt.secret}") secret: String,
    @param:Value("\${jwt.expiration-hours}") private val expirationHours: Long
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateToken(username: String, tenantId: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationHours * 3600 * 1000)

        return Jwts.builder()
            .subject(username)
            .claim("tenant_id", tenantId)
            .issuer("claim-poc")
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact()
    }

    fun validateAndExtract(token: String): Map<String, Any>? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .payload

            mapOf(
                "sub" to (claims.subject ?: return null),
                "tenant_id" to (claims["tenant_id"] ?: return null),
                "iss" to (claims.issuer ?: return null)
            )
        } catch (e: Exception) {
            null
        }
    }
}
