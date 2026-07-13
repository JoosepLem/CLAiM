package ee.claimai.auth

import ee.claimai.config.AppSecurityProperties
import ee.claimai.invoice.InvoiceService
import ee.claimai.invoice.dto.InvoiceLineRequest
import ee.claimai.invoice.dto.InvoiceType
import ee.claimai.invoice.dto.InvoiceUploadRequest
import ee.claimai.security.JwtService
import ee.claimai.tenant.TenantRepository
import ee.claimai.user.UserRepository
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.random.Random

@Controller
class AuthController(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val securityProperties: AppSecurityProperties,
    private val tenantRepository: TenantRepository,
    private val invoiceService: InvoiceService
) {

    @GetMapping("/login")
    fun loginForm(): String = "login"

    @PostMapping("/login")
    fun login(@RequestParam username: String, response: HttpServletResponse): String {
        val user = userRepository.findByUsername(username) ?: return "login"
        val token = jwtService.generateToken(username, user.tenantId, user.role)
        val cookie = ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .sameSite("Strict")
            .secure(securityProperties.secureCookie)
            .path("/")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return if (user.role == "ADMIN") "redirect:/admin" else "redirect:/dashboard"
    }

    @GetMapping("/dashboard")
    fun dashboard(
        @CookieValue("jwt", required = false) token: String?,
        model: Model
    ): String {
        if (token == null) return "redirect:/login"
        val claims = jwtService.validateAndExtract(token) ?: return "redirect:/login"
        val username = claims["sub"] as String
        val tenantId = claims["tenant_id"] as? String ?: return "redirect:/admin"
        val invoices = invoiceService.listTreatmentInvoices()
        model.addAttribute("username", username)
        val tenant = tenantRepository.findByTenantId(tenantId)
        model.addAttribute("clinicName", tenant?.name ?: "Unknown Clinic")
        model.addAttribute("invoices", invoices)
        model.addAttribute("logoutUrl", "/logout")
        return "dashboard"
    }

    @GetMapping("/dashboard/invoice/{id}")
    fun invoiceDetail(
        @CookieValue("jwt", required = false) token: String?,
        @PathVariable id: Long,
        model: Model
    ): String {
        if (token == null) return "redirect:/login"
        val claims = jwtService.validateAndExtract(token) ?: return "redirect:/login"
        val username = claims["sub"] as String
        val tenantId = claims["tenant_id"] as? String ?: return "redirect:/admin"
        val invoice = invoiceService.getTreatment(id)
        model.addAttribute("username", username)
        model.addAttribute("invoice", invoice)
        model.addAttribute("logoutUrl", "/logout")
        return "invoice-detail"
    }

    @PostMapping("/dashboard/seed")
    fun seedBenchmark(@RequestParam(defaultValue = "1000") lines: Int): String {
        require(lines in 1..10000) { "Lines must be between 1 and 10000" }

        val request = InvoiceUploadRequest(
            type = InvoiceType.TREATMENT,
            invoiceNumber = "BENCH-${System.currentTimeMillis()}",
            sourceFilename = "benchmark-seed.json",
            lines = (1..lines).map { generateFakeLine(it) }
        )
        invoiceService.upload(request)
        return "redirect:/dashboard"
    }

    @GetMapping("/logout")
    fun logout(response: HttpServletResponse): String {
        val cookie = ResponseCookie.from("jwt", "")
            .httpOnly(true)
            .sameSite("Strict")
            .secure(securityProperties.secureCookie)
            .path("/")
            .maxAge(0)
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return "redirect:/login"
    }

    private fun generateFakeLine(index: Int) = InvoiceLineRequest(
        isikukood = listOf(3, 4, 5, 6).random().toString() +
            "%02d".format(Random.nextInt(0, 99)) +
            "%02d".format(Random.nextInt(1, 12)) +
            "%02d".format(Random.nextInt(1, 28)) +
            "%04d".format(Random.nextInt(0, 9999)),
        procedureCode = "PROC-${(Random.nextInt(9000) + 1000)}",
        amount = BigDecimal(Random.nextInt(100, 5000)),
        date = LocalDate.of(2025, 1, 1).plusDays(index.toLong() % 365)
    )
}
