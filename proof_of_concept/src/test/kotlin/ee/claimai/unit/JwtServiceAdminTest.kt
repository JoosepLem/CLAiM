package ee.claimai.unit

import ee.claimai.config.JwtProperties
import ee.claimai.security.JwtService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtServiceAdminTest {

    private val jwtProperties = JwtProperties(secret = "test-secret-that-is-at-least-32-bytes-long", expirationHours = 8)
    private val jwtService = JwtService(jwtProperties)

    @Test
    fun `admin token contains ROLE_ADMIN authority and no tenant_id`() {
        val token = jwtService.generateToken("admin", null, "ADMIN")
        val claims = jwtService.validateAndExtract(token)

        assertThat(claims).isNotNull
        assertThat(claims!!["sub"]).isEqualTo("admin")
        assertThat(claims["authorities"]).isEqualTo(listOf("ROLE_ADMIN"))
        assertThat(claims).doesNotContainKey("tenant_id")
    }

    @Test
    fun `clinic employee token contains ROLE_CLINIC_EMPLOYEE authority and tenant_id`() {
        val token = jwtService.generateToken("user_a", "tenant_a", "CLINIC_EMPLOYEE")
        val claims = jwtService.validateAndExtract(token)

        assertThat(claims).isNotNull
        assertThat(claims!!["sub"]).isEqualTo("user_a")
        assertThat(claims["authorities"]).isEqualTo(listOf("ROLE_CLINIC_EMPLOYEE"))
        assertThat(claims["tenant_id"]).isEqualTo("tenant_a")
    }

    @Test
    fun `validateAndExtract returns authorities for admin token`() {
        val token = jwtService.generateToken("admin", null, "ADMIN")
        val claims = jwtService.validateAndExtract(token)

        assertThat(claims).isNotNull
        @Suppress("UNCHECKED_CAST")
        val authorities = claims!!["authorities"] as? List<String>
        assertThat(authorities).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `tokens for different roles produce different authority values`() {
        val adminToken = jwtService.generateToken("admin", null, "ADMIN")
        val employeeToken = jwtService.generateToken("user", "tenant", "CLINIC_EMPLOYEE")

        val adminClaims = jwtService.validateAndExtract(adminToken)
        val employeeClaims = jwtService.validateAndExtract(employeeToken)

        assertThat(adminClaims!!["authorities"]).isEqualTo(listOf("ROLE_ADMIN"))
        assertThat(employeeClaims!!["authorities"]).isEqualTo(listOf("ROLE_CLINIC_EMPLOYEE"))
    }
}
