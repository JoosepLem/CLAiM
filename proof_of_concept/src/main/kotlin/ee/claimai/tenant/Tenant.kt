package ee.claimai.tenant

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("tenants")
data class Tenant(
    @Id val id: Long = 0,
    val tenantId: String,
    val name: String
)
