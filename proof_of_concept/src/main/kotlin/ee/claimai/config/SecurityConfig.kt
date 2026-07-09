package ee.claimai.config

import ee.claimai.security.JwtAuthenticationFilter
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter
) {
    private val log = LoggerFactory.getLogger(SecurityConfig::class.java)

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/login", "/logout", "/", "/actuator/health", "/error").permitAll()
                    .requestMatchers("/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated()
            }
            .exceptionHandling { eh ->
                eh.accessDeniedHandler { request, response, ex ->
                    log.warn("Access denied: {} {} — {}", request.method, request.requestURI, ex.message)
                    response.contentType = "application/problem+json"
                    response.status = HttpStatus.FORBIDDEN.value()
                    response.writer.write(
                        """{"type":"about:blank","title":"Forbidden","status":403,"detail":"Access denied"}"""
                    )
                }
                eh.authenticationEntryPoint { request, response, ex ->
                    log.warn("Authentication required: {} {} — {}", request.method, request.requestURI, ex.message)
                    response.contentType = "application/problem+json"
                    response.status = HttpStatus.UNAUTHORIZED.value()
                    response.writer.write(
                        """{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required"}"""
                    )
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }
}
