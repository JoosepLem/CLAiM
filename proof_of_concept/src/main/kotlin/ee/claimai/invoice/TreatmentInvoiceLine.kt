package ee.claimai.invoice

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import java.time.LocalDate

@Table("treatment_invoice_lines")
data class TreatmentInvoiceLine(
    @Id val id: Long = 0,
    val invoiceId: Long,
    val isikukood: ByteArray,
    val isikukoodHash: ByteArray,
    val procedureCode: String,
    val amount: BigDecimal,
    val treatmentDate: LocalDate,
    val keyVersion: Int = 1
)
