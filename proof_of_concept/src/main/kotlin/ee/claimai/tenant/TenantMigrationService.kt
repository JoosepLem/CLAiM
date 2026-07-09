package ee.claimai.tenant

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import javax.sql.DataSource

@Service
class TenantMigrationService(
    @Qualifier("migrationDataSource") private val migrationDataSource: DataSource
) {
    private val sharedJdbc = JdbcTemplate(migrationDataSource)

    fun migrateAll() {
        migrateShared()
        val schemas = sharedJdbc.queryForList(
            "SELECT tenant_id FROM tenants WHERE active = true", String::class.java
        ).filterNotNull()
        val failed = mutableListOf<String>()
        for (schema in schemas) {
            try {
                migrateTenant(schema)
            } catch (e: FlywayException) {
                log.error("Migration failed for tenant schema {}", schema, e)
                failed.add(schema)
            }
        }
        if (failed.isNotEmpty()) {
            throw IllegalStateException("Tenant migrations failed for: $failed")
        }
    }

    fun migrateShared() {
        Flyway.configure()
            .dataSource(migrationDataSource)
            .schemas("public")
            .locations("classpath:db/migration/shared")
            .load()
            .migrate()
    }

    fun migrateTenant(schema: String) {
        validateSchemaName(schema)
        Flyway.configure()
            .dataSource(migrationDataSource)
            .schemas(schema)
            .locations("classpath:db/migration/tenant")
            .placeholders(mapOf("schema" to schema))
            .load()
            .migrate()
    }

    private fun validateSchemaName(schema: String) {
        if (!schema.matches(Regex("[a-z][a-z0-9_]{0,62}"))) {
            throw IllegalArgumentException("Invalid schema name: $schema")
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(TenantMigrationService::class.java)
    }
}
