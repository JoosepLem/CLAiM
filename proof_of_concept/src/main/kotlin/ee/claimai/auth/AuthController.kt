package ee.claimai.auth

import ee.claimai.config.AppSecurityProperties
import ee.claimai.invoice.TreatmentInvoiceRepository
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
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AuthController(
    private val userRepository: UserRepository,
    private val jwtService: JwtService,
    private val securityProperties: AppSecurityProperties,
    private val treatmentInvoiceRepository: TreatmentInvoiceRepository,
    private val tenantRepository: TenantRepository
) {

    @GetMapping("/login")
    fun loginForm(): String = "login"

    @PostMapping("/login")
    fun login(@RequestParam username: String, response: HttpServletResponse): String {
        val user = userRepository.findByUsername(username) ?: return "login"
        val token = jwtService.generateToken(username, user.tenantId)
        val cookie = ResponseCookie.from("jwt", token)
            .httpOnly(true)
            .sameSite("Strict")
            .secure(securityProperties.secureCookie)
            .path("/")
            .build()
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString())
        return "redirect:/dashboard"
    }

    @GetMapping("/dashboard")
    fun dashboard(
        @CookieValue("jwt", required = false) token: String?,
        model: Model
    ): String {
        if (token == null) return "redirect:/login"
        val claims = jwtService.validateAndExtract(token) ?: return "redirect:/login"
        val username = claims["sub"] as String
        val tenantId = claims["tenant_id"] as String
        val invoices = treatmentInvoiceRepository.findAllByOrderByUploadedAtDesc()
        model.addAttribute("username", username)
        val tenant = tenantRepository.findByTenantId(tenantId)
        model.addAttribute("clinicName", tenant?.name ?: "Unknown Clinic")
        model.addAttribute("invoices", invoices)
        model.addAttribute("logoutUrl", "/logout")
        return "dashboard"
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
}
