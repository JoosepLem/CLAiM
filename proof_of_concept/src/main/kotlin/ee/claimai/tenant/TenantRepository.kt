package ee.claimai.tenant

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface TenantRepository : CrudRepository<Tenant, Long> {
    fun findByTenantId(tenantId: String): Tenant?
}
