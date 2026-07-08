package ee.claimai.integration

import ee.claimai.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantIsolationTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val restTemplate = RestTemplate()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM tenant_a.treatment_invoice_lines")
        jdbcTemplate.update("DELETE FROM tenant_a.treatment_invoices")
        jdbcTemplate.update("DELETE FROM tenant_a.partner_invoice_lines")
        jdbcTemplate.update("DELETE FROM tenant_a.partner_invoices")
        jdbcTemplate.update("DELETE FROM tenant_b.treatment_invoice_lines")
        jdbcTemplate.update("DELETE FROM tenant_b.treatment_invoices")
        jdbcTemplate.update("DELETE FROM tenant_b.partner_invoice_lines")
        jdbcTemplate.update("DELETE FROM tenant_b.partner_invoices")

        val tenantAInvoiceId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoices (id, invoice_number, source_filename) VALUES (?, ?, ?)",
            tenantAInvoiceId, "TA-INV-001", "ta_file.pdf"
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoice_lines (id, invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), tenantAInvoiceId, byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), "PROC-A", 100.00, java.sql.Date.valueOf("2025-01-15")
        )

        val tenantBInvoiceId = UUID.randomUUID()
        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoices (id, invoice_number, source_filename) VALUES (?, ?, ?)",
            tenantBInvoiceId, "TB-INV-001", "tb_file.pdf"
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoice_lines (id, invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), tenantBInvoiceId, byteArrayOf(7, 8, 9), byteArrayOf(10, 11, 12), "PROC-B", 200.00, java.sql.Date.valueOf("2025-02-20")
        )
    }

    @Test
    fun `login page is publicly accessible`() {
        val response = restTemplate.getForEntity(
            "http://localhost:$port/login", String::class.java
        )
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("<h1>Login</h1>")
    }

    @Test
    fun `protected page without JWT returns 401`() {
        try {
            restTemplate.getForEntity(
                "http://localhost:$port/dashboard", String::class.java
            )
            assertThat(false).withFailMessage("Expected 401 but got success").isTrue()
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Test
    fun `api invoices without JWT returns 401`() {
        try {
            restTemplate.getForEntity(
                "http://localhost:$port/api/invoices", String::class.java
            )
            assertThat(false).withFailMessage("Expected 401 but got success").isTrue()
        } catch (e: HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
        }
    }

    @Test
    fun `login sets JWT cookie with security attributes`() {
        val loginHeaders = HttpHeaders()
        loginHeaders.contentType = MediaType.APPLICATION_FORM_URLENCODED
        val loginBody = LinkedMultiValueMap<String, String>()
        loginBody.add("username", "user_a")
        val loginRequest = HttpEntity(loginBody, loginHeaders)

        val loginResponse = restTemplate.postForEntity(
            "http://localhost:$port/login", loginRequest, String::class.java
        )

        assertThat(loginResponse.statusCode.is3xxRedirection).isTrue()

        val setCookieHeader = loginResponse.headers["Set-Cookie"]?.firstOrNull()
        assertThat(setCookieHeader).isNotNull
        assertThat(setCookieHeader).contains("HttpOnly")
        assertThat(setCookieHeader).contains("SameSite=Strict")
    }

    @Test
    fun `user_a sees only tenant_a invoices`() {
        val jwt = loginAs("user_a")

        val headers = HttpHeaders()
        headers.add("Cookie", "jwt=$jwt")
        val response = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("TA-INV-001")
        assertThat(response.body).doesNotContain("TB-INV-001")
    }

    @Test
    fun `user_b sees only tenant_b invoices`() {
        val jwt = loginAs("user_b")

        val headers = HttpHeaders()
        headers.add("Cookie", "jwt=$jwt")
        val response = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("TB-INV-001")
        assertThat(response.body).doesNotContain("TA-INV-001")
    }

    @Test
    fun `user_a dashboard shows correct clinic name`() {
        val jwt = loginAs("user_a")

        val headers = HttpHeaders()
        headers.add("Cookie", "jwt=$jwt")
        val response = restTemplate.exchange(
            "http://localhost:$port/dashboard",
            HttpMethod.GET,
            HttpEntity<Any>(headers),
            String::class.java
        )

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("Demo Clinic 1")
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        val body = LinkedMultiValueMap<String, String>()
        body.add("username", username)
        val request = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(
            "http://localhost:$port/login", request, String::class.java
        )

        val setCookieHeader = response.headers["Set-Cookie"]?.firstOrNull()
            ?: throw IllegalStateException("No Set-Cookie header in login response")
        return setCookieHeader.split(";").first().removePrefix("jwt=")
    }
}
