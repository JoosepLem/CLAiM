package ee.claimai.invoice

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
class PartnerInvoiceRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insert(invoice: PartnerInvoice): Long {
        val sql = """INSERT INTO partner_invoices (invoice_number, provider_name, source_filename)
                     VALUES (?, ?, ?) RETURNING id"""
        return jdbcTemplate.queryForObject(sql, Long::class.java,
            invoice.invoiceNumber, invoice.providerName, invoice.sourceFilename
        ) ?: throw IllegalStateException("Failed to insert partner invoice")
    }

    fun insertLine(line: PartnerInvoiceLine): Long {
        val sql = """INSERT INTO partner_invoice_lines
            (invoice_id, isikukood, isikukood_hash, procedure_code, amount, service_date, key_version)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"""
        return jdbcTemplate.queryForObject(sql, Long::class.java,
            line.invoiceId, line.isikukood, line.isikukoodHash,
            line.procedureCode, line.amount, line.serviceDate, line.keyVersion
        ) ?: throw IllegalStateException("Failed to insert partner invoice line")
    }

    fun findAllOrderByUploadedAtDesc(): List<PartnerInvoice> {
        return jdbcTemplate.query(
            "SELECT id, invoice_number, provider_name, uploaded_at, source_filename FROM partner_invoices ORDER BY uploaded_at DESC",
            { rs, _ ->
                PartnerInvoice(
                    id = rs.getLong("id"),
                    invoiceNumber = rs.getString("invoice_number"),
                    providerName = rs.getString("provider_name"),
                    uploadedAt = rs.getObject("uploaded_at", Instant::class.java),
                    sourceFilename = rs.getString("source_filename")
                )
            }
        )
    }

    fun findById(id: Long): PartnerInvoice? {
        return jdbcTemplate.query(
            "SELECT id, invoice_number, provider_name, uploaded_at, source_filename FROM partner_invoices WHERE id = ?",
            { rs, _ ->
                PartnerInvoice(
                    id = rs.getLong("id"),
                    invoiceNumber = rs.getString("invoice_number"),
                    providerName = rs.getString("provider_name"),
                    uploadedAt = rs.getObject("uploaded_at", Instant::class.java),
                    sourceFilename = rs.getString("source_filename")
                )
            },
            id
        ).firstOrNull()
    }

    fun findLinesByInvoiceId(invoiceId: Long): List<PartnerInvoiceLine> {
        return jdbcTemplate.query(
            """SELECT id, invoice_id, isikukood, isikukood_hash, procedure_code, amount, service_date, key_version
               FROM partner_invoice_lines WHERE invoice_id = ?""",
            { rs, _ ->
                PartnerInvoiceLine(
                    id = rs.getLong("id"),
                    invoiceId = rs.getLong("invoice_id"),
                    isikukood = rs.getBytes("isikukood"),
                    isikukoodHash = rs.getBytes("isikukood_hash"),
                    procedureCode = rs.getString("procedure_code"),
                    amount = rs.getBigDecimal("amount"),
                    serviceDate = rs.getObject("service_date", LocalDate::class.java),
                    keyVersion = rs.getInt("key_version")
                )
            },
            invoiceId
        )
    }
}
