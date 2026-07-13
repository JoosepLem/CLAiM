package ee.claimai.invoice.dto

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class InvoiceResponse(
    val id: Long,
    val type: InvoiceType,
    val invoiceNumber: String?,
    val uploadedAt: Instant,
    val sourceFilename: String?,
    val providerName: String?,
    val lines: List<InvoiceLineResponse>
)

data class InvoiceLineResponse(
    val id: Long,
    val isikukood: String,
    val procedureCode: String,
    val amount: BigDecimal,
    val date: LocalDate
)
