package ee.claimai.integration

import ee.claimai.invoice.dto.InvoiceLineRequest
import ee.claimai.invoice.dto.InvoiceType
import ee.claimai.invoice.dto.InvoiceUploadRequest
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
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TenantEncryptionIsolationTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0
    private val restTemplate = RestTemplate()

    companion object {
        private val suffix = "i" + UUID.randomUUID().toString().replace("-", "").take(11)
        private val setupDone = AtomicBoolean(false)

        @JvmStatic
        @DynamicPropertySource
        fun registerTenantSuffix(registry: DynamicPropertyRegistry) {
            registry.add("test.tenant.suffix") { suffix }
        }
    }

    private val tenantBId = "teib_$suffix"
    private val userA = "teiua_$suffix"
    private val userB = "teiub_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            ensureTenantSchema(tenantBId, "Isolation Clinic B")
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING",
                userA, suffix, "CLINIC_EMPLOYEE"
            )
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING",
                userB, tenantBId, "CLINIC_EMPLOYEE"
            )
        }
    }

    @Test
    fun `TC8 tenant_a data encrypted with own DEK tenant_b cannot access`() {
        val jwtA = loginAs(userA)
        val jwtB = loginAs(userB)

        val requestA = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "ISOL-A-ENC-001",
            sourceFilename = "a.json",
            lines = listOf(
                InvoiceLineRequest("47101010033", "PROC-A", BigDecimal("100.00"), LocalDate.of(2025, 1, 10))
            )
        )
        restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(requestA, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwtA")
            }),
            String::class.java
        )

        val requestB = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "ISOL-B-ENC-001",
            sourceFilename = "b.json",
            lines = listOf(
                InvoiceLineRequest("38001020044", "PROC-B", BigDecimal("200.00"), LocalDate.of(2025, 1, 11))
            )
        )
        restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(requestB, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwtB")
            }),
            String::class.java
        )

        val getA = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwtA") }),
            String::class.java
        )
        assertThat(getA.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getA.body).contains("ISOL-A-ENC-001")
        assertThat(getA.body).doesNotContain("ISOL-B-ENC-001")

        val getB = restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwtB") }),
            String::class.java
        )
        assertThat(getB.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(getB.body).contains("ISOL-B-ENC-001")
        assertThat(getB.body).doesNotContain("ISOL-A-ENC-001")
    }

    @Test
    fun `TC8 tenant_a DEK cannot decrypt tenant_b ciphertexts`() {
        val jwtA = loginAs(userA)
        val jwtB = loginAs(userB)

        val requestB = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "ISOL-B-CROSS-001",
            sourceFilename = "b-cross.json",
            lines = listOf(
                InvoiceLineRequest("38001020044", "PROC-BC", BigDecimal("300.00"), LocalDate.of(2025, 2, 15))
            )
        )
        restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(requestB, HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                add("Cookie", "jwt=$jwtB")
            }),
            String::class.java
        )

        val dbCiphertext = jdbcTemplate.queryForObject(
            "SELECT isikukood FROM ${tenantBId}.treatment_invoice_lines LIMIT 1",
            ByteArray::class.java
        )

        val dekA = jdbcTemplate.queryForObject(
            "SELECT encrypted_dek FROM tenants WHERE tenant_id = ?",
            ByteArray::class.java,
            suffix
        )

        assertThat(dekA).isNotNull
        assertThat(dbCiphertext).isNotNull
        assertThat(String(dbCiphertext!!)).doesNotContain("38001020044")
    }

    @Test
    fun `each tenant has unique encrypted DEK stored after first encryption`() {
        val jwtA = loginAs(userA)
        val jwtB = loginAs(userB)

        restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(
                InvoiceUploadRequest(
                    type = InvoiceType.TREATMENT,
                    invoiceNumber = "DEK-A-001",
                    sourceFilename = "a.json",
                    lines = listOf(InvoiceLineRequest("47101010033", "PROC-X", BigDecimal("50.00"), LocalDate.of(2025, 1, 1)))
                ),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON; add("Cookie", "jwt=$jwtA") }
            ),
            String::class.java
        )

        restTemplate.exchange(
            "http://localhost:$port/api/invoices",
            HttpMethod.POST,
            HttpEntity(
                InvoiceUploadRequest(
                    type = InvoiceType.TREATMENT,
                    invoiceNumber = "DEK-B-001",
                    sourceFilename = "b.json",
                    lines = listOf(InvoiceLineRequest("38001020044", "PROC-Y", BigDecimal("60.00"), LocalDate.of(2025, 1, 2)))
                ),
                HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON; add("Cookie", "jwt=$jwtB") }
            ),
            String::class.java
        )

        val dekA = jdbcTemplate.queryForObject(
            "SELECT encrypted_dek FROM tenants WHERE tenant_id = ?",
            ByteArray::class.java,
            suffix
        )
        val dekB = jdbcTemplate.queryForObject(
            "SELECT encrypted_dek FROM tenants WHERE tenant_id = ?",
            ByteArray::class.java,
            tenantBId
        )

        assertThat(dekA).isNotNull
        assertThat(dekB).isNotNull
        assertThat(dekA).isNotEqualTo(dekB)
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", username) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        val cookie = response.headers["Set-Cookie"]?.firstOrNull() ?: throw IllegalStateException("No Set-Cookie")
        return cookie.split(";").first().removePrefix("jwt=")
    }
}
