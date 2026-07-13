package ee.claimai.invoice

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("treatment_invoices")
data class TreatmentInvoice(
    @Id val id: Long = 0,
    val invoiceNumber: String? = null,
    val uploadedAt: Instant = Instant.now(),
    val sourceFilename: String? = null
)
