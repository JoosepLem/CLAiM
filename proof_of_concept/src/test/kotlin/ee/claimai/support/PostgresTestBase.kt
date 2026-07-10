package ee.claimai.support

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

@Testcontainers
abstract class PostgresTestBase {

    @Autowired
    private lateinit var migrations: ee.claimai.tenant.TenantMigrationService

    @Autowired
    protected lateinit var jdbcTemplate: JdbcTemplate

    protected fun ensureTenantSchema(tenantId: String, tenantName: String) {
        jdbcTemplate.update(
            "INSERT INTO tenants (tenant_id, name, active) VALUES (?, ?, ?) ON CONFLICT (tenant_id) DO NOTHING",
            tenantId, tenantName, true
        )
        migrations.migrateTenant(tenantId)
    }

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("claim")
            .withUsername("claim")
            .withPassword("claim")
            .withCommand("postgres", "-c", "fsync=off", "-c", "synchronous_commit=off",
                "-c", "full_page_writes=off", "-c", "max_connections=50")
            .withTmpFs(mapOf("/var/lib/postgresql/data" to "rw"))

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            ensureTestRoles()
            registry.add("spring.datasource.hikari.jdbc-url") { postgres.jdbcUrl }
            registry.add("spring.datasource.hikari.username") { postgres.username }
            registry.add("spring.datasource.hikari.password") { postgres.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 2 }
            registry.add("app.datasource.migration.jdbc-url") { postgres.jdbcUrl }
            registry.add("app.datasource.migration.username") { postgres.username }
            registry.add("app.datasource.migration.password") { postgres.password }
            registry.add("app.datasource.migration.maximum-pool-size") { 2 }
        }

        @JvmStatic
        private fun ensureTestRoles() {
            try {
                DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
                    conn.createStatement().use { stmt ->
                        stmt.execute("CREATE ROLE app_user WITH LOGIN PASSWORD 'restricted_pass'")
                        stmt.execute("CREATE ROLE app_migrator WITH LOGIN PASSWORD 'restricted_pass'")
                    }
                }
            } catch (e: Exception) {
                // roles may already exist from a previous context in the same container
            }
        }
    }
}
