package ee.claimai.invoice

import java.math.BigDecimal
import java.time.LocalDate

data class PartnerInvoiceLine(
    val id: Long = 0,
    val invoiceId: Long,
    val isikukood: ByteArray,
    val isikukoodHash: ByteArray,
    val procedureCode: String,
    val amount: BigDecimal,
    val serviceDate: LocalDate,
    val keyVersion: Int = 1
)
