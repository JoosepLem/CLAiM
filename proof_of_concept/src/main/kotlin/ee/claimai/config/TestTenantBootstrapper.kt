package ee.claimai.config

import ee.claimai.tenant.TenantMigrationService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import javax.sql.DataSource

@Component
@Order(2)
@Profile("dev", "docker")
class TestTenantBootstrapper(
    private val jdbcTemplate: JdbcTemplate,
    private val migrations: TenantMigrationService,
    private val env: Environment,
    @Qualifier("migrationDataSource") private val migrationDataSource: DataSource
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        val suffix = env.getProperty("test.tenant.suffix") ?: return
        val tenantId = suffix

        val migJdbc = JdbcTemplate(migrationDataSource)
        try { migJdbc.execute("DROP ROLE IF EXISTS app_user") } catch (_: Exception) {}
        try { migJdbc.execute("CREATE ROLE app_user WITH LOGIN PASSWORD 'restricted_pass'") } catch (_: Exception) {}

        jdbcTemplate.update(
            "INSERT INTO tenants (tenant_id, name, active) VALUES (?, ?, ?) ON CONFLICT (tenant_id) DO NOTHING",
            tenantId, "Test $tenantId", true
        )

        migrations.migrateTenant(tenantId)
    }
}
