package ee.claimai.invoice

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class TreatmentInvoiceLineRepository(private val jdbcTemplate: JdbcTemplate) {

    fun insert(line: TreatmentInvoiceLine): Long {
        val sql = """INSERT INTO treatment_invoice_lines 
            (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date, key_version)
            VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id"""
        return jdbcTemplate.queryForObject(sql, Long::class.java,
            line.invoiceId, line.isikukood, line.isikukoodHash,
            line.procedureCode, line.amount, line.treatmentDate, line.keyVersion
        ) ?: throw IllegalStateException("Failed to insert treatment invoice line")
    }

    fun insertBatch(lines: List<TreatmentInvoiceLine>): List<Long> {
        if (lines.isEmpty()) return emptyList()
        val sql = StringBuilder("INSERT INTO treatment_invoice_lines (invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date, key_version) VALUES ")
        val params = mutableListOf<Any>()
        lines.forEachIndexed { i, line ->
            if (i > 0) sql.append(", ")
            sql.append("(?, ?, ?, ?, ?, ?, ?)")
            params.addAll(listOf(line.invoiceId, line.isikukood, line.isikukoodHash, line.procedureCode, line.amount, line.treatmentDate, line.keyVersion))
        }
        sql.append(" RETURNING id")
        return jdbcTemplate.queryForList(sql.toString(), Long::class.java, *params.toTypedArray()).mapNotNull { it }
    }

    fun findByInvoiceId(invoiceId: Long): List<TreatmentInvoiceLine> {
        return jdbcTemplate.query(
            """SELECT id, invoice_id, isikukood, isikukood_hash, procedure_code, amount, treatment_date, key_version
               FROM treatment_invoice_lines WHERE invoice_id = ?""",
            { rs, _ ->
                TreatmentInvoiceLine(
                    id = rs.getLong("id"),
                    invoiceId = rs.getLong("invoice_id"),
                    isikukood = rs.getBytes("isikukood"),
                    isikukoodHash = rs.getBytes("isikukood_hash"),
                    procedureCode = rs.getString("procedure_code"),
                    amount = rs.getBigDecimal("amount"),
                    treatmentDate = rs.getObject("treatment_date", LocalDate::class.java),
                    keyVersion = rs.getInt("key_version")
                )
            },
            invoiceId
        )
    }
}
