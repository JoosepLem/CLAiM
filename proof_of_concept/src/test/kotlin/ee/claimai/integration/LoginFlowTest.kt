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
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginFlowTest : PostgresTestBase() {

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

    private val testUser = "usr_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING",
                testUser, suffix, "CLINIC_EMPLOYEE"
            )
        }
    }

    @Test
    fun `login with user shows dashboard with clinic name`() {
        val loginHeaders = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val loginBody = LinkedMultiValueMap<String, String>().apply { add("username", testUser) }
        val loginResponse = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(loginBody, loginHeaders), String::class.java)
        assertThat(loginResponse.statusCode.is3xxRedirection).isTrue()

        val setCookie = loginResponse.headers["Set-Cookie"]?.firstOrNull()
        assertThat(setCookie).isNotNull
        val jwtValue = setCookie!!.split(";").first().removePrefix("jwt=")

        val dashHeaders = HttpHeaders().apply { add("Cookie", "jwt=$jwtValue") }
        val dashResponse = restTemplate.exchange("http://localhost:$port/dashboard", HttpMethod.GET, HttpEntity<Any>(dashHeaders), String::class.java)
        assertThat(dashResponse.statusCode.is2xxSuccessful).isTrue()
        assertThat(dashResponse.body).contains("Welcome,")
        assertThat(dashResponse.body).contains(testUser)
    }

    @Test
    fun `login with unknown username stays on login page`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", "unknown_user") }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        assertThat(response.statusCode.is2xxSuccessful).isTrue()
        assertThat(response.body).contains("<h1>Login</h1>")
    }
}
