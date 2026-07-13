package ee.claimai.invoice.dto

import java.math.BigDecimal
import java.time.LocalDate

enum class InvoiceType {
    TREATMENT, PARTNER
}

data class InvoiceUploadRequest(
    val type: InvoiceType,
    val invoiceNumber: String? = null,
    val sourceFilename: String? = null,
    val providerName: String? = null,
    val lines: List<InvoiceLineRequest>
)

data class InvoiceLineRequest(
    val isikukood: String,
    val procedureCode: String,
    val amount: BigDecimal,
    val date: LocalDate
)
