package ee.claimai.admin

import ee.claimai.tenant.Tenant
import ee.claimai.tenant.TenantMigrationService
import ee.claimai.tenant.TenantRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class AdminTenantService(
    private val tenantRepository: TenantRepository,
    private val tenantMigrationService: TenantMigrationService,
    @Qualifier("migrationDataSource") private val migrationDataSource: DataSource
) {
    private val jdbcTemplate = JdbcTemplate(migrationDataSource)

    fun createTenant(tenantId: String, name: String): Tenant {
        val tenant = tenantRepository.save(Tenant(tenantId = tenantId, name = name, active = true))
        jdbcTemplate.execute("CREATE SCHEMA \"$tenantId\"")
        tenantMigrationService.migrateTenant(tenantId)
        jdbcTemplate.execute("GRANT USAGE ON SCHEMA \"$tenantId\" TO app_user")
        jdbcTemplate.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA \"$tenantId\" TO app_user")
        jdbcTemplate.execute("ALTER DEFAULT PRIVILEGES FOR ROLE app_migrator IN SCHEMA \"$tenantId\" GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_user")
        return tenant
    }

    fun deleteTenant(tenantId: String) {
        jdbcTemplate.execute("DROP SCHEMA \"$tenantId\" CASCADE")
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenantId)
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId)
    }

    fun findAll(): List<Tenant> = tenantRepository.findAll().toList()
}
