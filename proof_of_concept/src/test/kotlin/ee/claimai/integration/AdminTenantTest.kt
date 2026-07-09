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
class AdminTenantTest : PostgresTestBase() {

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

    @BeforeEach
    fun setUp() {
        if (setupDone.compareAndSet(false, true)) {
            jdbcTemplate.update(
                "INSERT INTO users (username, tenant_id, role) VALUES (?, ?, ?) ON CONFLICT (username) DO NOTHING",
                adminUser, null, "ADMIN"
            )
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
    fun `admin creates tenant end to end`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("tenantId", "test_clinic"); add("name", "Test Clinic") }
        val resp = restTemplate.postForEntity("http://localhost:$port/admin/tenants/create", HttpEntity(body, headers), String::class.java)
        assertThat(resp.statusCode.is3xxRedirection).isTrue()
        val tenants = jdbcTemplate.queryForList("SELECT tenant_id, name FROM tenants WHERE tenant_id = ?", "test_clinic")
        assertThat(tenants).hasSize(1)
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS test_clinic CASCADE")
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = 'test_clinic'")
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = 'test_clinic'")
    }

    @Test
    fun `admin can see created tenant in admin dashboard`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("tenantId", "visible_clinic"); add("name", "Visible Clinic") }
        restTemplate.postForEntity("http://localhost:$port/admin/tenants/create", HttpEntity(body, headers), String::class.java)
        val response = restTemplate.exchange("http://localhost:$port/admin", HttpMethod.GET, HttpEntity<Any>(HttpHeaders().apply { add("Cookie", "jwt=$jwt") }), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).contains("visible_clinic")
        jdbcTemplate.execute("DROP SCHEMA IF EXISTS visible_clinic CASCADE")
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = 'visible_clinic'")
    }

    @Test
    fun `admin deletes tenant end to end`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("tenantId", "delete_me"); add("name", "Delete Me") }
        restTemplate.postForEntity("http://localhost:$port/admin/tenants/create", HttpEntity(body, headers), String::class.java)
        jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT DO NOTHING", "delete_user", "delete_me", "CLINIC_EMPLOYEE")
        val deleteBody = LinkedMultiValueMap<String, String>().apply { add("tenantId", "delete_me") }
        val resp = restTemplate.postForEntity("http://localhost:$port/admin/tenants/delete", HttpEntity(deleteBody, headers), String::class.java)
        assertThat(resp.statusCode.is3xxRedirection).isTrue()
        assertThat(jdbcTemplate.queryForList("SELECT tenant_id FROM tenants WHERE tenant_id = ?", "delete_me")).isEmpty()
        assertThat(jdbcTemplate.queryForList("SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?", "delete_me")).isEmpty()
    }

    @Test
    fun `tenant delete cleans up all associated data`() {
        val jwt = loginAs(adminUser)
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_FORM_URLENCODED; add("Cookie", "jwt=$jwt") }
        val body = LinkedMultiValueMap<String, String>().apply { add("tenantId", "full_cleanup"); add("name", "Full Cleanup") }
        restTemplate.postForEntity("http://localhost:$port/admin/tenants/create", HttpEntity(body, headers), String::class.java)
        jdbcTemplate.update("INSERT INTO users (username, tenant_id, role) VALUES (?,?,?) ON CONFLICT DO NOTHING", "cleanup_user", "full_cleanup", "CLINIC_EMPLOYEE")
        jdbcTemplate.update("INSERT INTO full_cleanup.treatment_invoices (invoice_number, source_filename) VALUES (?,?)", "CLEANUP-001", "cleanup.pdf")
        restTemplate.postForEntity("http://localhost:$port/admin/tenants/delete", HttpEntity(LinkedMultiValueMap<String, String>().apply { add("tenantId", "full_cleanup") }, headers), String::class.java)
        assertThat(jdbcTemplate.queryForList("SELECT tenant_id FROM tenants WHERE tenant_id = ?", "full_cleanup")).isEmpty()
        assertThat(jdbcTemplate.queryForList("SELECT username FROM users WHERE tenant_id = ?", "full_cleanup")).isEmpty()
        assertThat(jdbcTemplate.queryForList("SELECT schema_name FROM information_schema.schemata WHERE schema_name = ?", "full_cleanup")).isEmpty()
    }
}
