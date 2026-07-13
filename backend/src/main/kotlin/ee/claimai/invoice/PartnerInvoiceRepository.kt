package ee.claimai.invoice

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface PartnerInvoiceRepository : CrudRepository<PartnerInvoice, Long> {

    @Query("SELECT id, invoice_number, provider_name, uploaded_at, source_filename FROM partner_invoices ORDER BY uploaded_at DESC")
    fun findAllByOrderByUploadedAtDesc(): List<PartnerInvoice>
}
