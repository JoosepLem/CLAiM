package ee.claimai.unit

import ee.claimai.auth.AuthController
import ee.claimai.config.AppSecurityProperties
import ee.claimai.invoice.TreatmentInvoiceRepository
import ee.claimai.security.JwtService
import ee.claimai.tenant.TenantRepository
import ee.claimai.user.User
import ee.claimai.user.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.servlet.View
import org.springframework.web.servlet.ViewResolver
import org.springframework.web.servlet.view.RedirectView

class LoginRedirectTest {

    private val userRepository: UserRepository = mockk()
    private val jwtService: JwtService = mockk()
    private val treatmentInvoiceRepository: TreatmentInvoiceRepository = mockk()
    private val tenantRepository: TenantRepository = mockk()

    private val dummyView = object : View {
        override fun render(model: MutableMap<String, *>?, request: jakarta.servlet.http.HttpServletRequest, response: jakarta.servlet.http.HttpServletResponse) {
            response.writer.write("<html><body><h1>Login</h1></body></html>")
        }
        override fun getContentType(): String? = "text/html"
    }

    private val viewResolver = ViewResolver { viewName, _ ->
        if (viewName.startsWith("redirect:")) {
            RedirectView(viewName.removePrefix("redirect:"))
        } else {
            dummyView
        }
    }

    private val mockMvc = MockMvcBuilders
        .standaloneSetup(AuthController(userRepository, jwtService, AppSecurityProperties(secureCookie = false), treatmentInvoiceRepository, tenantRepository))
        .setViewResolvers(viewResolver)
        .build()

    @Test
    fun `GET login returns 200 with login form`() {
        val result = mockMvc.perform(get("/login"))
            .andExpect(status().isOk)
            .andReturn()

        assertThat(result.response.contentAsString).contains("<h1>Login</h1>")
    }

    @Test
    fun `GET dashboard without cookie redirects to login`() {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/login"))
    }

    @Test
    fun `POST login with valid username sets JWT cookie and redirects to dashboard`() {
        every { userRepository.findByUsername("user_a") } returns User(username = "user_a", tenantId = "tenant_a", role = "CLINIC_EMPLOYEE")
        every { jwtService.generateToken("user_a", "tenant_a", "CLINIC_EMPLOYEE") } returns "test.jwt.token"

        mockMvc.perform(post("/login").param("username", "user_a"))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/dashboard"))
            .andExpect(cookie().exists("jwt"))
    }
}
