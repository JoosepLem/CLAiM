package ee.claimai.invoice

import ee.claimai.encryption.EncryptionService
import ee.claimai.invoice.dto.InvoiceLineResponse
import ee.claimai.invoice.dto.InvoiceResponse
import ee.claimai.invoice.dto.InvoiceType
import ee.claimai.invoice.dto.InvoiceUploadRequest
import ee.claimai.tenant.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class InvoiceService(
    private val encryptionService: EncryptionService,
    private val treatmentInvoiceRepo: TreatmentInvoiceRepository,
    private val treatmentInvoiceLineRepo: TreatmentInvoiceLineRepository,
    private val partnerInvoiceRepo: PartnerInvoiceRepository,
    private val partnerInvoiceLineRepo: PartnerInvoiceLineRepository
) {

    fun upload(request: InvoiceUploadRequest): InvoiceResponse {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant context")
        val totalStart = System.currentTimeMillis()

        val encryptStart = System.currentTimeMillis()
        val encrypted = request.lines.map { line ->
            val result = encryptionService.encrypt(tenantId, line.isikukood)
            Triple(line, result.ciphertext, result.hmac)
        }
        val encryptMs = System.currentTimeMillis() - encryptStart

        val response = when (request.type) {
            InvoiceType.TREATMENT -> {
                val invoice = TreatmentInvoice(
                    invoiceNumber = request.invoiceNumber,
                    sourceFilename = request.sourceFilename
                )
                val saved = treatmentInvoiceRepo.save(invoice)

                val lines = encrypted.map { (lineReq, ct, hmac) ->
                    TreatmentInvoiceLine(
                        invoiceId = saved.id,
                        isikukood = ct,
                        isikukoodHash = hmac,
                        procedureCode = lineReq.procedureCode,
                        amount = lineReq.amount,
                        treatmentDate = lineReq.date,
                        keyVersion = 1
                    )
                }
                val savedLines = treatmentInvoiceLineRepo.saveAll(lines).toList()
                val lineResponses = savedLines.zip(encrypted).map { (savedLine, pair) ->
                    InvoiceLineResponse(savedLine.id, pair.first.isikukood, pair.first.procedureCode, pair.first.amount, pair.first.date)
                }

                InvoiceResponse(
                    id = saved.id,
                    type = InvoiceType.TREATMENT,
                    invoiceNumber = saved.invoiceNumber,
                    uploadedAt = saved.uploadedAt,
                    sourceFilename = saved.sourceFilename,
                    providerName = null,
                    lines = lineResponses
                )
            }

            InvoiceType.PARTNER -> {
                if (request.providerName == null) throw IllegalArgumentException("providerName is required for partner invoices")

                val invoice = PartnerInvoice(
                    invoiceNumber = request.invoiceNumber,
                    providerName = request.providerName,
                    sourceFilename = request.sourceFilename
                )
                val saved = partnerInvoiceRepo.save(invoice)

                val lines = encrypted.map { (lineReq, ct, hmac) ->
                    PartnerInvoiceLine(
                        invoiceId = saved.id,
                        isikukood = ct,
                        isikukoodHash = hmac,
                        procedureCode = lineReq.procedureCode,
                        amount = lineReq.amount,
                        serviceDate = lineReq.date,
                        keyVersion = 1
                    )
                }
                val savedLines = partnerInvoiceLineRepo.saveAll(lines).toList()
                val lineResponses = savedLines.zip(encrypted).map { (savedLine, pair) ->
                    InvoiceLineResponse(savedLine.id, pair.first.isikukood, pair.first.procedureCode, pair.first.amount, pair.first.date)
                }

                InvoiceResponse(
                    id = saved.id,
                    type = InvoiceType.PARTNER,
                    invoiceNumber = saved.invoiceNumber,
                    uploadedAt = saved.uploadedAt,
                    sourceFilename = saved.sourceFilename,
                    providerName = saved.providerName,
                    lines = lineResponses
                )
            }
        }

        val totalMs = System.currentTimeMillis() - totalStart
        log.info(
            "POST /api/invoices type={} lines={} encrypt_ms={} avg_us_per_line={} total_ms={}",
            request.type,
            request.lines.size,
            encryptMs,
            if (request.lines.isNotEmpty()) (encryptMs * 1000) / request.lines.size else 0,
            totalMs
        )

        return response
    }

    fun listTreatmentInvoices(): List<InvoiceResponse> {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant context")
        return treatmentInvoiceRepo.findAllByOrderByUploadedAtDesc().map { inv ->
            val lines = treatmentInvoiceLineRepo.findByInvoiceId(inv.id)
            InvoiceResponse(
                id = inv.id,
                type = InvoiceType.TREATMENT,
                invoiceNumber = inv.invoiceNumber,
                uploadedAt = inv.uploadedAt,
                sourceFilename = inv.sourceFilename,
                providerName = null,
                lines = lines.map { decryptLine(tenantId, it) }
            )
        }
    }

    fun listAll(): List<InvoiceResponse> {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant context")

        val treatmentInvoices = treatmentInvoiceRepo.findAllByOrderByUploadedAtDesc().map { inv ->
            val lines = treatmentInvoiceLineRepo.findByInvoiceId(inv.id)
            val id = inv.id
            InvoiceResponse(
                id = id,
                type = InvoiceType.TREATMENT,
                invoiceNumber = inv.invoiceNumber,
                uploadedAt = inv.uploadedAt,
                sourceFilename = inv.sourceFilename,
                providerName = null,
                lines = lines.map { decryptLine(tenantId, it) }
            )
        }

        val partnerInvoices = partnerInvoiceRepo.findAllByOrderByUploadedAtDesc().map { inv ->
            val lines = partnerInvoiceLineRepo.findByInvoiceId(inv.id)
            InvoiceResponse(
                id = inv.id,
                type = InvoiceType.PARTNER,
                invoiceNumber = inv.invoiceNumber,
                uploadedAt = inv.uploadedAt,
                sourceFilename = inv.sourceFilename,
                providerName = inv.providerName,
                lines = lines.map { decryptLine(tenantId, it) }
            )
        }

        return (treatmentInvoices + partnerInvoices).sortedByDescending { it.uploadedAt }
    }

    fun getTreatment(id: Long): InvoiceResponse {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant context")
        val invoice = treatmentInvoiceRepo.findById(id).orElseThrow { NoSuchElementException("Treatment invoice not found: $id") }
        val lines = treatmentInvoiceLineRepo.findByInvoiceId(id)

        val decryptStart = System.currentTimeMillis()
        val lineResponses = lines.map { decryptLine(tenantId, it) }
        val decryptMs = System.currentTimeMillis() - decryptStart

        log.info(
            "GET /api/invoices/treatment/{} lines={} decrypt_ms={} avg_us_per_line={}",
            id,
            lines.size,
            decryptMs,
            if (lines.isNotEmpty()) (decryptMs * 1000) / lines.size else 0
        )

        return InvoiceResponse(
            id = invoice.id,
            type = InvoiceType.TREATMENT,
            invoiceNumber = invoice.invoiceNumber,
            uploadedAt = invoice.uploadedAt,
            sourceFilename = invoice.sourceFilename,
            providerName = null,
            lines = lineResponses
        )
    }

    fun getPartner(id: Long): InvoiceResponse {
        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant context")
        val invoice = partnerInvoiceRepo.findById(id).orElseThrow { NoSuchElementException("Partner invoice not found: $id") }
        val lines = partnerInvoiceLineRepo.findByInvoiceId(id)

        val decryptStart = System.currentTimeMillis()
        val lineResponses = lines.map { decryptLine(tenantId, it) }
        val decryptMs = System.currentTimeMillis() - decryptStart

        log.info(
            "GET /api/invoices/partner/{} lines={} decrypt_ms={} avg_us_per_line={}",
            id,
            lines.size,
            decryptMs,
            if (lines.isNotEmpty()) (decryptMs * 1000) / lines.size else 0
        )

        return InvoiceResponse(
            id = invoice.id,
            type = InvoiceType.PARTNER,
            invoiceNumber = invoice.invoiceNumber,
            uploadedAt = invoice.uploadedAt,
            sourceFilename = invoice.sourceFilename,
            providerName = invoice.providerName,
            lines = lineResponses
        )
    }

    private fun decryptLine(tenantId: String, line: TreatmentInvoiceLine): InvoiceLineResponse {
        val isikukood = encryptionService.decrypt(tenantId, line.isikukood, line.keyVersion)
        return InvoiceLineResponse(line.id, isikukood, line.procedureCode, line.amount, line.treatmentDate)
    }

    private fun decryptLine(tenantId: String, line: PartnerInvoiceLine): InvoiceLineResponse {
        val isikukood = encryptionService.decrypt(tenantId, line.isikukood, line.keyVersion)
        return InvoiceLineResponse(line.id, isikukood, line.procedureCode, line.amount, line.serviceDate)
    }

    companion object {
        private val log = LoggerFactory.getLogger(InvoiceService::class.java)
    }
}
