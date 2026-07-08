package ee.claimai.invoice

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class InvoiceController(private val treatmentInvoiceRepository: TreatmentInvoiceRepository) {

    @GetMapping("/invoices")
    fun listInvoices(): List<TreatmentInvoice> {
        return treatmentInvoiceRepository.findAllByOrderByUploadedAtDesc()
    }
}
