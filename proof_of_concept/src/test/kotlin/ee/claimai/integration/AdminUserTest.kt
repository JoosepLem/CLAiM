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
import org.springframework.web.client.RestTemplate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminUserTest : PostgresTestBase() {

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
    private val tenantBId = "utb_$suffix"
    private val userA = "ua_$suffix"
    private val userB = "ub_$suffix"

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", adminUser, null, "ADMIN")
            jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", userA, suffix, "CLINIC_EMPLOYEE")
            ensureTenantSchema(tenantBId, "Tenant B")
            jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", userB, tenantBId, "CLINIC_EMPLOYEE")
        }
    }

    private fun loginAs(username: String): String {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", username) }
        val response = restTemplate.postForEntity("http://localhost:$port/login", HttpEntity(body, headers), String::class.java)
        val cookie = response.headers["Set-Cookie"]?.firstOrNull() ?: throw IllegalStateException("No Set-Cookie")
        return cookie.split(";").first().removePrefix("jwt=")
    }

    @Test
    fun `admin creates clinic employee user`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", "test_user"); add("tenantId", suffix); add("role", "CLINIC_EMPLOYEE") }
        assertThat(restTemplate.postForEntity("http://localhost:$port/admin/users/create", HttpEntity(body, headers), String::class.java).statusCode.is3xxRedirection).isTrue()
        val users = jdbcTemplate.queryForList("SELECT username, tenant_id, role FROM users WHERE username = ?", "test_user")
        assertThat(users).hasSize(1)
        assertThat(users[0]["tenant_id"]).isEqualTo(suffix)
    }

    @Test
    fun `admin creates admin user with null tenant`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", "new_admin"); add("role", "ADMIN") }
        restTemplate.postForEntity("http://localhost:$port/admin/users/create", HttpEntity(body, headers), String::class.java)
        val users = jdbcTemplate.queryForList("SELECT username, tenant_id, role FROM users WHERE username = ?", "new_admin")
        assertThat(users).hasSize(1)
        assertThat(users[0]["tenant_id"]).isNull()
        assertThat(users[0]["role"]).isEqualTo("ADMIN")
    }

    @Test
    fun `admin sees user in admin dashboard after creation`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("username", "visible_user"); add("tenantId", suffix); add("role", "CLINIC_EMPLOYEE") }
        restTemplate.postForEntity("http://localhost:$port/admin/users/create", HttpEntity(body, headers), String::class.java)
        val resp = restTemplate.exchange("http://localhost:$port/admin", HttpMethod.GET, HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }), String::class.java)
        assertThat(resp.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(resp.body).contains("visible_user")
    }

    @Test
    fun `admin edits user`() {
        jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", "edit_source_user", suffix, "CLINIC_EMPLOYEE")
        val userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long::class.java, "edit_source_user")!!
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("id", userId.toString()); add("username", "edit_renamed_user"); add("tenantId", tenantBId); add("role", "ADMIN") }
        assertThat(restTemplate.postForEntity("http://localhost:$port/admin/users/update", HttpEntity(body, headers), String::class.java).statusCode.is3xxRedirection).isTrue()
        val users = jdbcTemplate.queryForList("SELECT username, tenant_id, role FROM users WHERE id = ?", userId)
        assertThat(users[0]["username"]).isEqualTo("edit_renamed_user")
        assertThat(users[0]["tenant_id"]).isEqualTo(tenantBId)
    }

    @Test
    fun `admin deletes user`() {
        jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT (username) DO NOTHING", "delete_test_user", suffix, "CLINIC_EMPLOYEE")
        val userId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE username = ?", Long::class.java, "delete_test_user")!!
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        restTemplate.postForEntity("http://localhost:$port/admin/users/delete", HttpEntity(LinkedMultiValueMap<String, String>().apply { add("id", userId.toString()) }, headers), String::class.java)
        assertThat(jdbcTemplate.queryForList("SELECT username FROM users WHERE id = ?", userId)).isEmpty()
    }

    @Test
    fun `admin filters users by tenant`() {
        val jwt = loginAs(adminUser)
        val response = restTemplate.exchange("http://localhost:$port/admin?tenantFilter=$suffix", HttpMethod.GET, HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains(userA)
        assertThat(response.body).doesNotContain(userB)
    }
}
