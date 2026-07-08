package ee.claimai.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.security")
data class AppSecurityProperties(
    var secureCookie: Boolean = false
)
