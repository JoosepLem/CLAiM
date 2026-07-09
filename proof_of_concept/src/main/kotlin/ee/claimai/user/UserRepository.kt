package ee.claimai.user

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRepository : CrudRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun findByRole(role: String): List<User>
    fun findByTenantId(tenantId: String): List<User>
}
