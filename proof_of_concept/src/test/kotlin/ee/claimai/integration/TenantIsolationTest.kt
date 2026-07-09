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
class TenantIsolationTest : PostgresTestBase() {

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

    private val tenantBId = "tib_$suffix"
    private val userA = "ua_$suffix"
    private val userB = "ub_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            ensureTenantSchema(tenantBId, "Isolation Clinic B")
            jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", userA, suffix, "CLINIC_EMPLOYEE")
            jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", userB, tenantBId, "CLINIC_EMPLOYEE")
        }
    }

    private fun seedInvoiceData(schema: String, invoiceNo: String, procCode: String) {
        jdbcTemplate.update("DELETE FROM $schema.treatment_invoice_lines")
        jdbcTemplate.update("DELETE FROM $schema.treatment_invoices")
        val invId = jdbcTemplate.queryForObject(
            "INSERT INTO $schema.treatment_invoices (invoice_number, source_filename) VALUES (?, ?) RETURNING id",
            Long::class.java, invoiceNo, "$schema-file.pdf"
        )!!
        jdbcTemplate.update(
            "INSERT INTO $schema.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?,?,?,?,?,?)",
            invId, byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), procCode, java.math.BigDecimal("100.00"), java.sql.Date.valueOf("2025-01-15")
        )
    }

    @Test
    fun `login page is publicly accessible`() {
        val response = restTemplate.getForEntity("http://localhost:$port/login", String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("<h1>Login</h1>")
    }

    @Test
    fun `protected page without JWT returns 401`() {
        try {
            restTemplate.getForEntity("http://localhost:$port/dashboard", String::class.java)
            assertThat(false).withFailMessage("Expected 401").isTrue()
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Test
    fun `api invoices without JWT returns 401`() {
        try {
            restTemplate.getForEntity("http://localhost:$port/api/invoices", String::class.java)
            assertThat(false).withFailMessage("Expected 401").isTrue()
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Test
    fun `login sets JWT cookie with security attributes`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", userA) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        assertThat(response.statusCode.is3xxRedirection).isTrue()
        val cookie = response.headers["Set-Cookie"]?.firstOrNull()
        assertThat(cookie).contains("HttpOnly")
        assertThat(cookie).contains("SameSite=Strict")
    }

    @Test
    fun `user_a sees only tenant_a invoices`() {
        seedInvoiceData(suffix, "ISOL-A-INV-001", "PROC-A")
        seedInvoiceData(tenantBId, "ISOL-B-INV-001", "PROC-B")
        val jwt = loginAs(userA)
        val response = restTemplate.exchange("http://localhost:$port/api/invoices", HttpMethod.GET, HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("ISOL-A-INV-001")
        assertThat(response.body).doesNotContain("ISOL-B-INV-001")
    }

    @Test
    fun `user_b sees only tenant_b invoices`() {
        seedInvoiceData(suffix, "ISOL-A-INV-002", "PROC-C")
        seedInvoiceData(tenantBId, "ISOL-B-INV-002", "PROC-D")
        val jwt = loginAs(userB)
        val response = restTemplate.exchange("http://localhost:$port/api/invoices", HttpMethod.GET, HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("ISOL-B-INV-002")
        assertThat(response.body).doesNotContain("ISOL-A-INV-002")
    }

    @Test
    fun `user_a dashboard shows correct clinic name`() {
        val jwt = loginAs(userA)
        val response = restTemplate.exchange("http://localhost:$port/dashboard", HttpMethod.GET, HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Test $suffix")
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", username) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        val cookie = response.headers["Set-Cookie"]?.firstOrNull() ?: throw IllegalStateException("No Set-Cookie")
        return cookie.split(";").first().removePrefix("jwt=")
    }
}
