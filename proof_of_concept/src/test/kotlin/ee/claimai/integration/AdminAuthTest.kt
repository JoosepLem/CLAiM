package ee.claimai.integration

import ee.claimai.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminAuthTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0
    private val restTemplate = RestTemplate()

    companion object {
        private val suffix = "t" + UUID.randomUUID().toString().replace("-", "").take(11)
        private val setupDone = AtomicBoolean(false)

        @JvmStatic
        @DynamicPropertySource
        fun registerTenantSuffix(registry: DynamicPropertyRegistry) {
            registry.add("test.tenant.suffix") { suffix }
        }
    }

    private val adminUser = "adm_$suffix"
    private val employeeUser = "emp_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING",
                adminUser, null, "ADMIN"
            )
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING",
                employeeUser, suffix, "CLINIC_EMPLOYEE"
            )
        }
    }

    @Test
    fun `admin login redirects to admin`() {
        val loginHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", adminUser) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, loginHeaders), String::class.java)
        assertThat(response.statusCode.is3xxRedirection).isTrue()
        assertThat(response.headers.location.toString()).contains("/admin")
    }

    @Test
    fun `clinic employee login redirects to dashboard`() {
        val loginHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", employeeUser) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, loginHeaders), String::class.java)
        assertThat(response.statusCode.is3xxRedirection).isTrue()
        assertThat(response.headers.location.toString()).contains("/dashboard")
    }

    @Test
    fun `admin can access admin dashboard`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { add("Cookie", "jwt=$jwt") }
        val response = restTemplate.exchange("http://localhost:$port/admin", HttpMethod.GET, HttpEntity<Any>(headers), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Admin Dashboard")
    }

    @Test
    fun `clinic employee accessing admin returns 403`() {
        val jwt = loginAs(employeeUser)
        val headers = HttpHeaders().apply { add("Cookie", "jwt=$jwt") }
        try {
            restTemplate.exchange("http://localhost:$port/admin", HttpMethod.GET, HttpEntity<Any>(headers), String::class.java)
            assertThat(false).withFailMessage("Expected 403").isTrue()
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.FORBIDDEN)
        }
    }

    @Test
    fun `admin without JWT returns 401`() {
        try {
            restTemplate.getForEntity("http://localhost:$port/admin", String::class.java)
            assertThat(false).withFailMessage("Expected 401").isTrue()
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Test
    fun `admin accessing dashboard redirects to admin`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { add("Cookie", "jwt=$jwt") }
        val response = restTemplate.exchange("http://localhost:$port/dashboard", HttpMethod.GET, HttpEntity<Any>(headers), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Admin Dashboard")
        assertThat(response.body).doesNotContain("Treatment Invoices")
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", username) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        val cookie = response.headers["Set-Cookie"]?.firstOrNull() ?: throw IllegalStateException("No Set-Cookie")
        return cookie.split(";").first().removePrefix("jwt=")
    }
}
