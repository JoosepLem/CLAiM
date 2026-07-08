package ee.claimai.invoice

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TreatmentInvoiceRepository : CrudRepository<TreatmentInvoice, Long> {

    @Query("SELECT id, invoice_number, uploaded_at, source_filename FROM treatment_invoices ORDER BY uploaded_at DESC")
    fun findAllByOrderByUploadedAtDesc(): List<TreatmentInvoice>
}
