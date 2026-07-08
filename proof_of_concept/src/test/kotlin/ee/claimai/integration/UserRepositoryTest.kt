package ee.claimai.integration

import ee.claimai.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class UserRepositoryTest : PostgresTestBase() {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `insert and query user row`() {
        jdbcTemplate.update("INSERT INTO public.users (username, tenant_id) VALUES (?, ?)", "test_user", "test_tenant")

        val result = jdbcTemplate.queryForMap("SELECT username, tenant_id FROM public.users WHERE username = ?", "test_user")

        assertThat(result["username"]).isEqualTo("test_user")
        assertThat(result["tenant_id"]).isEqualTo("test_tenant")
    }
}
