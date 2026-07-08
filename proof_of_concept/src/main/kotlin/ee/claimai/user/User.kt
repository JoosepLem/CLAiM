package ee.claimai.user

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("users")
data class User(
    @Id val id: Long = 0,
    val username: String,
    val tenantId: String
)
