package ee.claimai.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "jwt")
data class JwtProperties(
    var secret: String = "",
    var expirationHours: Long = 8
)
