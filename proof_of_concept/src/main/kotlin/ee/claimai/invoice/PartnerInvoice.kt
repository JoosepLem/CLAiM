package ee.claimai.invoice

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("partner_invoices")
data class PartnerInvoice(
    @Id val id: Long = 0,
    val invoiceNumber: String? = null,
    val providerName: String,
    val uploadedAt: Instant = Instant.now(),
    val sourceFilename: String? = null
)
