package ee.claimai.integration

import com.zaxxer.hikari.HikariDataSource
import ee.claimai.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AppUserPrivilegesTest : PostgresTestBase() {

    @Autowired
    private lateinit var dataSource: DataSource

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private lateinit var restrictedTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        executeIgnoreConcurrent("CREATE ROLE app_user WITH LOGIN PASSWORD 'restricted_pass'")

        executeIgnoreConcurrent("GRANT CONNECT ON DATABASE claim TO app_user")
        executeIgnoreConcurrent("GRANT USAGE ON SCHEMA tenant_a TO app_user")
        executeIgnoreConcurrent("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tenant_a TO app_user")
        executeIgnoreConcurrent("GRANT USAGE ON SCHEMA tenant_b TO app_user")
        executeIgnoreConcurrent("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA tenant_b TO app_user")

        val hikariDs = dataSource.unwrap(HikariDataSource::class.java)
        val restrictedDs = DriverManagerDataSource()
        restrictedDs.setUrl(hikariDs.jdbcUrl)
        restrictedDs.setUsername("app_user")
        restrictedDs.setPassword("restricted_pass")
        restrictedTemplate = JdbcTemplate(restrictedDs)
    }

    @Test
    fun `app_user can read from tenant tables`() {
        val invoiceId = jdbcTemplate.queryForObject(
            "INSERT INTO tenant_a.treatment_invoices (invoice_number, source_filename) VALUES (?, ?) RETURNING id",
            Long::class.java, "TEST-INV", "test.csv"
        )

        val rows = restrictedTemplate.queryForList(
            "SELECT * FROM tenant_a.treatment_invoices WHERE id = ?", invoiceId
        )
        assertThat(rows).hasSize(1)
    }

    @Test
    fun `app_user cannot create tables`() {
        assertThatThrownBy {
            restrictedTemplate.execute("CREATE TABLE tenant_a.forbidden_table (id SERIAL PRIMARY KEY)")
        }.isInstanceOf(DataAccessException::class.java)
    }

    @Test
    fun `app_user cannot create schemas`() {
        assertThatThrownBy {
            restrictedTemplate.execute("CREATE SCHEMA forbidden_schema")
        }.isInstanceOf(DataAccessException::class.java)
    }

    private fun executeIgnoreConcurrent(sql: String) {
        try {
            jdbcTemplate.execute(sql)
        } catch (e: DataAccessException) {
            try {
                Thread.sleep((50L..150L).random())
                jdbcTemplate.execute(sql)
            } catch (retryException: DataAccessException) {
            }
        }
    }
}
