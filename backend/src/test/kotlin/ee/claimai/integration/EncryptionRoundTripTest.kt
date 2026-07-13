package ee.claimai.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import ee.claimai.invoice.dto.InvoiceLineRequest
import ee.claimai.invoice.dto.InvoiceType
import ee.claimai.invoice.dto.InvoiceUploadRequest
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
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EncryptionRoundTripTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0
    private val restTemplate = RestTemplate()

    private val objectMapper = jacksonObjectMapper()

    companion object {
        private val suffix = "e" + UUID.randomUUID().toString().replace("-", "").take(11)
        private val setupDone = AtomicBoolean(false)

        @JvmStatic
        @DynamicPropertySource
        fun registerTenantSuffix(registry: DynamicPropertyRegistry) {
            registry.add("test.tenant.suffix") { suffix }
        }
    }

    private val userA = "ert_${suffix}"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING",
                userA, suffix, "CLINIC_EMPLOYEE"
            )
        }
    }

    @Test
    fun `TC7 E2E upload treatment invoice then read it back with decrypted isikukood`() {
        val jwt = loginAs(userA)
        val request = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "TC7-INV-001",
            sourceFilename = "test.json",
            lines = listOf(
                InvoiceLineRequest("47101010033", "PROC-001", BigDecimal("150.00"), LocalDate.of(2025, 3, 15)),
                InvoiceLineRequest("38001020044", "PROC-002", BigDecimal("200.00"), LocalDate.of(2025, 3, 16))
            )
        )

        val postResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(request, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwt")
            }),
            String::class.java
        )
        assertThat(postResponse.statusCode).isEqualTo(HttpStatus.CREATED)

        val getResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(getResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getResponse.body).contains("TC7-INV-001")
        assertThat(getResponse.body).contains("47101010033")
        assertThat(getResponse.body).contains("38001020044")
    }

    @Test
    fun `TC7 database rows contain BYTEA ciphertexts not plaintext`() {
        val jwt = loginAs(userA)
        val isikukood = "47101010033"
        val request = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "TC7b-INV-002",
            sourceFilename = "test.json",
            lines = listOf(
                InvoiceLineRequest(isikukood, "PROC-003", BigDecimal("100.00"), LocalDate.of(2025, 4, 1))
            )
        )

        restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(request, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwt")
            }),
            String::class.java
        )

        val rows = jdbcTemplate.queryForList(
            "SELECT isikukood, isikukood_hash FROM ${suffix}.treatment_invoice_lines"
        )
        assertThat(rows).hasSize(1)
        val isikukoodBytes = rows[0]["isikukood"] as ByteArray
        val isikukoodStr = String(isikukoodBytes)
        assertThat(isikukoodStr).doesNotContain(isikukood)
    }

    @Test
    fun `TC11 invalid JSON body returns 400`() {
        val jwt = loginAs(userA)
        try {
            restTemplate.exchange(
                "http://localhost:$port/api/invoices",
                HttpMethod.POST,
                HttpEntity("not json", HttpHeaders().apply {
                    contentType = MediaType.APPLICATION_JSON
                    add("Cookie", "jwt=$jwt")
                }),
                String::class.java
            )
            assertThat(false).withFailMessage("Expected 400").isTrue()
        } catch (e: org.springframework.web.client.HttpClientErrorException) {
            assertThat(e.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
        }
    }

    @Test
    fun `POST partner invoice upload and read back correctly`() {
        val jwt = loginAs(userA)
        val request = InvoiceUploadRequest(
            type = InvoiceType.PARTNER,
            invoiceNumber = "TC7-PARTNER-001",
            providerName = "Central Lab",
            sourceFilename = "partner.json",
            lines = listOf(
                InvoiceLineRequest("47101010033", "LAB-001", BigDecimal("300.00"), LocalDate.of(2025, 5, 10))
            )
        )

        val postResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(request, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwt")
            }),
            String::class.java
        )
        assertThat(postResponse.statusCode).isEqualTo(HttpStatus.CREATED)

        val getResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(getResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getResponse.body).contains("TC7-PARTNER-001")
        assertThat(getResponse.body).contains("Central Lab")
        assertThat(getResponse.body).contains("47101010033")
    }

    @Test
    fun `GET single invoice by id returns correct decrypted data`() {
        val jwt = loginAs(userA)
        val request = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "TC7-SINGLE-001",
            sourceFilename = "single.json",
            lines = listOf(
                InvoiceLineRequest("47101010033", "PROC-X", BigDecimal("500.00"), LocalDate.of(2025, 6, 1))
            )
        )

        val postResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(request, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwt")
            }),
            String::class.java
        )
        val posted = objectMapper.readTree(postResponse.body)
        val invoiceId = posted.get("id").asLong()

        val getResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices/treatment/$invoiceId",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(getResponse.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getResponse.body).contains("TC7-SINGLE-001")
        assertThat(getResponse.body).contains("47101010033")
        assertThat(getResponse.body).contains("500.00")
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", username) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        val cookie = response.headers["Set-Cookie"]?.firstOrNull() ?: throw IllegalStateException("No Set-Cookie")
        return cookie.split(";").first().removePrefix("jwt=")
    }
}
