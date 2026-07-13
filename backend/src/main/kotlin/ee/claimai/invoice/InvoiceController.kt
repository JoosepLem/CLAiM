package ee.claimai.invoice

import ee.claimai.invoice.dto.InvoiceResponse
import ee.claimai.invoice.dto.InvoiceUploadRequest
import ee.claimai.invoice.dto.InvoiceType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class InvoiceController(
    private val invoiceService: InvoiceService
) {

    @PostMapping("/invoices")
    @ResponseStatus(HttpStatus.CREATED)
    fun upload(@RequestBody request: InvoiceUploadRequest): InvoiceResponse {
        validate(request)
        return invoiceService.upload(request)
    }

    @GetMapping("/invoices")
    fun listAll(): List<InvoiceResponse> {
        return invoiceService.listAll()
    }

    @GetMapping("/invoices/treatment/{id}")
    fun getTreatment(@PathVariable id: Long): InvoiceResponse {
        return invoiceService.getTreatment(id)
    }

    @GetMapping("/invoices/partner/{id}")
    fun getPartner(@PathVariable id: Long): InvoiceResponse {
        return invoiceService.getPartner(id)
    }

    private fun validate(request: InvoiceUploadRequest) {
        if (request.type == InvoiceType.PARTNER && request.providerName.isNullOrBlank()) {
            throw IllegalArgumentException("providerName is required for partner invoices")
        }
        if (request.lines.isEmpty()) {
            throw IllegalArgumentException("At least one line item is required")
        }
        for (line in request.lines) {
            if (line.isikukood.isBlank()) throw IllegalArgumentException("isikukood is required")
            if (line.procedureCode.isBlank()) throw IllegalArgumentException("procedureCode is required")
            if (line.amount <= java.math.BigDecimal.ZERO) throw IllegalArgumentException("amount must be positive")
        }
    }
}
