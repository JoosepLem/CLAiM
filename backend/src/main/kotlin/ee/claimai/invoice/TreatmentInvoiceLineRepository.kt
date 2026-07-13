package ee.claimai.invoice

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TreatmentInvoiceLineRepository : CrudRepository<TreatmentInvoiceLine, Long> {

    fun findByInvoiceId(invoiceId: Long): List<TreatmentInvoiceLine>
}
