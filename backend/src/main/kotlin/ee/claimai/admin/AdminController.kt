package ee.claimai.admin

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
@RequestMapping("/admin")
class AdminController(
    private val adminTenantService: AdminTenantService,
    private val adminUserService: AdminUserService
) {

    @GetMapping
    fun dashboard(
        @RequestParam(required = false) tenantFilter: String?,
        model: Model
    ): String {
        val tenants = adminTenantService.findAll()
        val users = if (tenantFilter != null && tenantFilter.isNotBlank()) {
            adminUserService.findByTenantId(tenantFilter)
        } else {
            adminUserService.findAll()
        }

        model.addAttribute("tenants", tenants)
        model.addAttribute("users", users)
        model.addAttribute("tenantFilter", tenantFilter ?: "")
        model.addAttribute("logoutUrl", "/logout")
        return "admin"
    }

    @PostMapping("/tenants/create")
    fun createTenant(@RequestParam tenantId: String, @RequestParam name: String): String {
        adminTenantService.createTenant(tenantId, name)
        return "redirect:/admin"
    }

    @PostMapping("/tenants/delete")
    fun deleteTenant(@RequestParam tenantId: String): String {
        adminTenantService.deleteTenant(tenantId)
        return "redirect:/admin"
    }

    @PostMapping("/users/create")
    fun createUser(
        @RequestParam username: String,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam role: String
    ): String {
        val resolvedTenantId = if (tenantId.isNullOrBlank()) null else tenantId
        adminUserService.createUser(username, resolvedTenantId, role)
        return "redirect:/admin"
    }

    @PostMapping("/users/update")
    fun updateUser(
        @RequestParam id: Long,
        @RequestParam username: String,
        @RequestParam(required = false) tenantId: String?,
        @RequestParam role: String
    ): String {
        val resolvedTenantId = if (tenantId.isNullOrBlank()) null else tenantId
        adminUserService.updateUser(id, username, resolvedTenantId, role)
        return "redirect:/admin"
    }

    @PostMapping("/users/delete")
    fun deleteUser(@RequestParam id: Long): String {
        adminUserService.deleteUser(id)
        return "redirect:/admin"
    }
}
