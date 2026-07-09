package ee.claimai.config

import ee.claimai.tenant.TenantMigrationService
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@Order(1)
@Profile("dev", "docker")
class DevSeedRunner(
    private val jdbcTemplate: JdbcTemplate,
    private val migrations: TenantMigrationService
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        migrations.migrateShared()

        jdbcTemplate.update(
            "INSERT INTO tenants (tenant_id, name, active) VALUES (?, ?, ?) ON CONFLICT (tenant_id) DO NOTHING",
            "tenant_a", "Demo Clinic 1", true
        )
        jdbcTemplate.update(
            "INSERT INTO tenants (tenant_id, name, active) VALUES (?, ?, ?) ON CONFLICT (tenant_id) DO NOTHING",
            "tenant_b", "Demo Clinic 2", true
        )

        jdbcTemplate.update(
            "INSERT INTO users (username, tenant_id) VALUES (?, ?) ON CONFLICT (username) DO NOTHING",
            "user_a", "tenant_a"
        )
        jdbcTemplate.update(
            "INSERT INTO users (username, tenant_id) VALUES (?, ?) ON CONFLICT (username) DO NOTHING",
            "user_b", "tenant_b"
        )

        migrations.migrateTenant("tenant_a")
        migrations.migrateTenant("tenant_b")

        insertSampleData()
    }

    private fun insertSampleData() {
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoices (invoice_number, source_filename) VALUES (?, ?)",
            "TA-INV-001", "ta_invoice_001.pdf"
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoices (invoice_number, source_filename) VALUES (?, ?)",
            "TA-INV-002", "ta_invoice_002.pdf"
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoices (invoice_number, source_filename) VALUES (?, ?)",
            "TA-INV-003", "ta_invoice_003.pdf"
        )

        val taInv1Id = jdbcTemplate.queryForObject(
            "SELECT id FROM tenant_a.treatment_invoices WHERE invoice_number = ?", Long::class.java, "TA-INV-001"
        )!!
        val taInv2Id = jdbcTemplate.queryForObject(
            "SELECT id FROM tenant_a.treatment_invoices WHERE invoice_number = ?", Long::class.java, "TA-INV-002"
        )!!

        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?)",
            taInv1Id, byteArrayOf(1, 2, 3), byteArrayOf(4, 5, 6), "3004", java.math.BigDecimal("150.00"), java.sql.Date.valueOf("2025-01-15")
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?)",
            taInv1Id, byteArrayOf(7, 8, 9), byteArrayOf(10, 11, 12), "3008", java.math.BigDecimal("200.00"), java.sql.Date.valueOf("2025-01-16")
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_a.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?)",
            taInv2Id, byteArrayOf(13, 14, 15), byteArrayOf(16, 17, 18), "3012", java.math.BigDecimal("350.00"), java.sql.Date.valueOf("2025-02-01")
        )

        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoices (invoice_number, source_filename) VALUES (?, ?)",
            "TB-INV-001", "tb_invoice_001.pdf"
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoices (invoice_number, source_filename) VALUES (?, ?)",
            "TB-INV-002", "tb_invoice_002.pdf"
        )

        val tbInv1Id = jdbcTemplate.queryForObject(
            "SELECT id FROM tenant_b.treatment_invoices WHERE invoice_number = ?", Long::class.java, "TB-INV-001"
        )!!
        val tbInv2Id = jdbcTemplate.queryForObject(
            "SELECT id FROM tenant_b.treatment_invoices WHERE invoice_number = ?", Long::class.java, "TB-INV-002"
        )!!

        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?)",
            tbInv1Id, byteArrayOf(17, 18, 19), byteArrayOf(20, 21, 22), "3004", java.math.BigDecimal("500.00"), java.sql.Date.valueOf("2025-03-10")
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?)",
            tbInv1Id, byteArrayOf(23, 24, 25), byteArrayOf(26, 27, 28), "3008", java.math.BigDecimal("600.00"), java.sql.Date.valueOf("2025-03-11")
        )
        jdbcTemplate.update(
            "INSERT INTO tenant_b.treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date) VALUES (?, ?, ?, ?, ?, ?)",
            tbInv2Id, byteArrayOf(29, 30, 31), byteArrayOf(32, 33, 34), "3012", java.math.BigDecimal("750.00"), java.sql.Date.valueOf("2025-03-12")
        )
    }
}
