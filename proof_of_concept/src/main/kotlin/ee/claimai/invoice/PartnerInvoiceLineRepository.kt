package ee.claimai.invoice

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PartnerInvoiceLineRepository : CrudRepository<PartnerInvoiceLine, Long> {

    fun findByInvoiceId(invoiceId: Long): List<PartnerInvoiceLine>
}
