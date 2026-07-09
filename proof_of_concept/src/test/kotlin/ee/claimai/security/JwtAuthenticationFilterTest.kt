package ee.claimai.security

import ee.claimai.config.JwtProperties
import ee.claimai.tenant.TenantContext
import jakarta.servlet.FilterChain
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest {

    private val jwtProperties = JwtProperties(secret = "test-secret-that-is-at-least-32-bytes-long-for-filter-tests", expirationHours = 8)
    private val jwtService = JwtService(jwtProperties)

    private val filter = JwtAuthenticationFilter(jwtService)

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `public paths pass through without setting tenant context`() {
        val publicPaths = listOf("/login", "/logout", "/", "/actuator/health", "/error")

        for (path in publicPaths) {
            val request = MockHttpServletRequest("GET", path)
            val response = MockHttpServletResponse()
            var chainCalled = false
            val chain = FilterChain { _, _ -> chainCalled = true }

            filter.doFilter(request, response, chain)

            assertThat(response.status).isEqualTo(200)
            assertThat(chainCalled).isTrue()
            assertThat(TenantContext.get()).isNull()
        }
    }

    @Test
    fun `missing JWT returns 401`() {
        val request = MockHttpServletRequest("GET", "/dashboard")
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `valid JWT sets tenant context and authentication`() {
        val token = jwtService.generateToken("testuser", "tenant_x", "CLINIC_EMPLOYEE")
        val request = MockHttpServletRequest("GET", "/api/invoices")
        request.setCookies(jakarta.servlet.http.Cookie("jwt", token))
        val response = MockHttpServletResponse()
        var chainCalled = false
        var tenantDuringChain: String? = null
        var authDuringChain: String? = null
        val chain = FilterChain { _, _ ->
            tenantDuringChain = TenantContext.get()
            authDuringChain = SecurityContextHolder.getContext().authentication?.name
            chainCalled = true
        }

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(chainCalled).isTrue()
        assertThat(tenantDuringChain).isEqualTo("tenant_x")
        assertThat(authDuringChain).isEqualTo("testuser")
    }

    @Test
    fun `invalid JWT returns 401`() {
        val token = jwtService.generateToken("user", "tenant", "CLINIC_EMPLOYEE")
        val tampered = token.dropLast(1) + "X"
        val request = MockHttpServletRequest("GET", "/dashboard")
        request.setCookies(jakarta.servlet.http.Cookie("jwt", tampered))
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `tenant context is cleared after request`() {
        val token = jwtService.generateToken("user", "tenant_x", "CLINIC_EMPLOYEE")
        val request = MockHttpServletRequest("GET", "/dashboard")
        request.setCookies(jakarta.servlet.http.Cookie("jwt", token))
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        filter.doFilter(request, response, chain)

        assertThat(TenantContext.get()).isNull()
    }

    @Test
    fun `expired JWT returns 401`() {
        val expiredService = JwtService(JwtProperties(secret = "test-secret-that-is-at-least-32-bytes-long-for-filter-tests", expirationHours = -1))
        val token = expiredService.generateToken("user", "tenant", "CLINIC_EMPLOYEE")
        val expiredFilter = JwtAuthenticationFilter(jwtService)
        val request = MockHttpServletRequest("GET", "/dashboard")
        request.setCookies(jakarta.servlet.http.Cookie("jwt", token))
        val response = MockHttpServletResponse()
        val chain = FilterChain { _, _ -> }

        expiredFilter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(401)
    }

    @Test
    fun `admin JWT does not set tenant context`() {
        val token = jwtService.generateToken("admin", null, "ADMIN")
        val request = MockHttpServletRequest("GET", "/admin")
        request.setCookies(jakarta.servlet.http.Cookie("jwt", token))
        val response = MockHttpServletResponse()
        var tenantDuringChain: String? = null
        val chain = FilterChain { _, _ ->
            tenantDuringChain = TenantContext.get()
        }

        filter.doFilter(request, response, chain)

        assertThat(response.status).isEqualTo(200)
        assertThat(tenantDuringChain).isNull()
    }
}
