package ee.claimai.security

import ee.claimai.tenant.TenantContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService
) : OncePerRequestFilter() {

    companion object {
        private val PUBLIC_PATHS = setOf("/login", "/login-debug", "/logout", "/", "/actuator/health", "/error")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        if (request.requestURI in PUBLIC_PATHS) {
            filterChain.doFilter(request, response)
            return
        }

        val token = request.cookies?.find { it.name == "jwt" }?.value
        if (token == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing JWT")
            return
        }

        val claims = jwtService.validateAndExtract(token)
        if (claims == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired JWT")
            return
        }

        val username = claims["sub"] as? String ?: run {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing subject in JWT")
            return
        }

        @Suppress("UNCHECKED_CAST")
        val authorities = (claims["authorities"] as? List<String>)?.map { SimpleGrantedAuthority(it) } ?: emptyList()
        val isAdmin = authorities.any { it.authority == "ROLE_ADMIN" }

        val authentication = UsernamePasswordAuthenticationToken(username, null, authorities)
        SecurityContextHolder.getContext().authentication = authentication

        val tenantId = claims["tenant_id"] as? String
        if (!isAdmin && tenantId != null) {
            TenantContext.set(tenantId)
        }

        try {
            filterChain.doFilter(request, response)
        } finally {
            SecurityContextHolder.clearContext()
            TenantContext.clear()
        }
    }
}
