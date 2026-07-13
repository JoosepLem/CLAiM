package ee.claimai.invoice

import ee.claimai.invoice.dto.InvoiceType
import ee.claimai.tenant.TenantContext
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random

@RestController
@RequestMapping("/api/benchmark")
class BenchmarkController(
    private val invoiceService: InvoiceService
) {

    @PostMapping("/seed")
    fun seed(@RequestParam(defaultValue = "1000") lines: Int): Map<String, Any> {
        require(lines in 1..10000) { "lines must be between 1 and 10000" }

        val tenantId = TenantContext.get() ?: throw IllegalStateException("No tenant context")
        val start = System.currentTimeMillis()

        val request = ee.claimai.invoice.dto.InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "BENCH-${System.currentTimeMillis()}",
            sourceFilename = "benchmark-seed.json",
            lines = (1..lines).map { generateFakeLine(it) }
        )

        val response = invoiceService.upload(request)
        val totalMs = System.currentTimeMillis() - start

        log.info(
            "BENCHMARK SEED lines={} total_ms={} encrypt_avg_us_per_line={} invoice_id={}",
            lines,
            totalMs,
            if (lines > 0) (totalMs * 1000) / lines else 0,
            response.id
        )

        return mapOf(
            "invoiceId" to response.id,
            "lines" to lines,
            "totalMs" to totalMs,
            "avgUsPerLine" to if (lines > 0) (totalMs * 1000) / lines else 0,
            "fetchUrl" to "/api/invoices/treatment/${response.id}"
        )
    }

    private fun generateFakeLine(index: Int) = ee.claimai.invoice.dto.InvoiceLineRequest(
        isikukood = generateFakeIsikukood(),
        procedureCode = "PROC-${(Random.nextInt(9000) + 1000)}",
        amount = BigDecimal(Random.nextInt(100, 5000)),
        date = LocalDate.of(2025, 1, 1).plusDays(index.toLong() % 365)
    )

    private fun generateFakeIsikukood(): String {
        val century = listOf(3, 4, 5, 6).random()
        val year = "%02d".format(Random.nextInt(0, 99))
        val month = "%02d".format(Random.nextInt(1, 12))
        val day = "%02d".format(Random.nextInt(1, 28))
        val sequence = "%04d".format(Random.nextInt(0, 9999))
        return "$century$year$month$day$sequence"
    }

    companion object {
        private val log = LoggerFactory.getLogger(BenchmarkController::class.java)
    }
}
