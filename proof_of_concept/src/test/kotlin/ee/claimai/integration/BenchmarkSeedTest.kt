package ee.claimai.integration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BenchmarkSeedTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0
    private val restTemplate = RestTemplate()
    private val objectMapper = jacksonObjectMapper()

    companion object {
        private val suffix = "b" + UUID.randomUUID().toString().replace("-", "").take(11)
        private val setupDone = AtomicBoolean(false)

        @JvmStatic
        @DynamicPropertySource
        fun registerTenantSuffix(registry: DynamicPropertyRegistry) {
            registry.add("test.tenant.suffix") { suffix }
        }
    }

    private val user = "bs_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING",
                user, suffix, "CLINIC_EMPLOYEE"
            )
        }
    }

    @Test
    fun `seed 50 line invoice then read back with correct decryption`() {
        val jwt = loginAs(user)

        val seedResponse = restTemplate.exchange(
            "http://localhost:$port/api/benchmark/seed?lines=50",
            HttpMethod.POST,
            HttpEntity<Any>(null, HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(seedResponse.statusCode).isEqualTo(HttpStatus.OK)

        val seedJson = objectMapper.readTree(seedResponse.body)
        val invoiceId = seedJson.get("invoiceId").asLong()
        val lineCount = seedJson.get("lines").asInt()
        assertThat(lineCount).isEqualTo(50)

        val fetchResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices/treatment/$invoiceId",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(fetchResponse.statusCode).isEqualTo(HttpStatus.OK)

        val fetchJson = objectMapper.readTree(fetchResponse.body)
        val lines = fetchJson.get("lines")
        assertThat(lines.size()).isEqualTo(50)

        for (line in lines) {
            val isikukood = line.get("isikukood").asText()
            assertThat(isikukood).matches(java.util.regex.Pattern.compile("^[3-6]\\d{10}$"))
        }
    }

    @Test
    fun `seed with default lines creates 1000 line invoice`() {
        val jwt = loginAs(user)

        val seedResponse = restTemplate.exchange(
            "http://localhost:$port/api/benchmark/seed",
            HttpMethod.POST,
            HttpEntity<Any>(null, HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(seedResponse.statusCode).isEqualTo(HttpStatus.OK)

        val seedJson = objectMapper.readTree(seedResponse.body)
        val invoiceId = seedJson.get("invoiceId").asLong()
        val lineCount = seedJson.get("lines").asInt()
        assertThat(lineCount).isEqualTo(1000)

        val fetchResponse = restTemplate.exchange(
            "http://localhost:$port/api/invoices/treatment/$invoiceId",
            HttpMethod.GET,
            HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(fetchResponse.statusCode).isEqualTo(HttpStatus.OK)

        val fetchJson = objectMapper.readTree(fetchResponse.body)
        assertThat(fetchJson.get("lines").size()).isEqualTo(1000)
    }

    @Test
    fun `seed results in BYTEA ciphertexts in database not plaintext`() {
        val jwt = loginAs(user)

        val seedResponse = restTemplate.exchange(
            "http://localhost:$port/api/benchmark/seed?lines=5",
            HttpMethod.POST,
            HttpEntity<Any>(null, HttpHeaders().apply { add("Cookie", "jwt=$jwt") }),
            String::class.java
        )
        assertThat(seedResponse.statusCode).isEqualTo(HttpStatus.OK)

        val seedJson = objectMapper.readTree(seedResponse.body)
        val invoiceId = seedJson.get("invoiceId").asLong()

        val rows = jdbcTemplate.queryForList(
            "SELECT isikukood FROM ${suffix}.treatment_invoice_lines WHERE invoice_id = ?",
            invoiceId
        )
        assertThat(rows).hasSize(5)
        for (row in rows) {
            val bytes = row["isikukood"] as ByteArray
            assertThat(bytes.size).isGreaterThan(12)
        }
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", username) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        val cookie = response.headers["Set-Cookie"]?.firstOrNull() ?: throw IllegalStateException("No Set-Cookie")
        return cookie.split(";").first().removePrefix("jwt=")
    }
}
