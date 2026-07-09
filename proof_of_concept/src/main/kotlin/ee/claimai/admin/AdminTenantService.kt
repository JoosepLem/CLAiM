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
        return tenant
    }

    fun deleteTenant(tenantId: String) {
        jdbcTemplate.execute("DROP SCHEMA \"$tenantId\" CASCADE")
        jdbcTemplate.update("DELETE FROM users WHERE tenant_id = ?", tenantId)
        jdbcTemplate.update("DELETE FROM tenants WHERE tenant_id = ?", tenantId)
    }

    fun findAll(): List<Tenant> = tenantRepository.findAll().toList()
}
