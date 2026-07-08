package ee.claimai.unit

import ee.claimai.config.JwtProperties
import ee.claimai.security.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtServiceTest {

    private val jwtProperties = JwtProperties(secret = "test-secret-that-is-at-least-32-bytes-long", expirationHours = 8)
    private val jwtService = JwtService(jwtProperties)

    @Test
    fun `generated token contains correct claims`() {
        val token = jwtService.generateToken("testuser", "tenant_x")
        val claims = jwtService.validateAndExtract(token)

        assertThat(claims).isNotNull
        assertThat(claims!!["sub"]).isEqualTo("testuser")
        assertThat(claims["tenant_id"]).isEqualTo("tenant_x")
        assertThat(claims["iss"]).isEqualTo("claim-poc")
    }

    @Test
    fun `validation succeeds on valid token`() {
        val token = jwtService.generateToken("valid_user", "valid_tenant")
        val claims = jwtService.validateAndExtract(token)

        assertThat(claims).isNotNull
        assertThat(claims!!["sub"]).isEqualTo("valid_user")
        assertThat(claims["tenant_id"]).isEqualTo("valid_tenant")
        assertThat(claims["iss"]).isEqualTo("claim-poc")
    }

    @Test
    fun `validation returns null on tampered token`() {
        val token = jwtService.generateToken("valid_user", "valid_tenant")
        val tampered = token.dropLast(1) + "X"

        val claims = jwtService.validateAndExtract(tampered)

        assertThat(claims).isNull()
    }

    @Test
    fun `validation returns null on expired token`() {
        val expiredService = JwtService(JwtProperties(secret = "test-secret-that-is-at-least-32-bytes-long", expirationHours = -1))
        val token = expiredService.generateToken("expired_user", "expired_tenant")
        val claims = jwtService.validateAndExtract(token)

        assertThat(claims).isNull()
    }
}
