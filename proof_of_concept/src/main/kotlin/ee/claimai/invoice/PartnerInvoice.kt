package ee.claimai.invoice

import java.time.Instant

data class PartnerInvoice(
    val id: Long = 0,
    val invoiceNumber: String? = null,
    val providerName: String,
    val uploadedAt: Instant = Instant.now(),
    val sourceFilename: String? = null
)
