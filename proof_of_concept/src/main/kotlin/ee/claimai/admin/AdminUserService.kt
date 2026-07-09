package ee.claimai.admin

import ee.claimai.user.User
import ee.claimai.user.UserRepository
import org.springframework.stereotype.Service

@Service
class AdminUserService(
    private val userRepository: UserRepository
) {
    fun createUser(username: String, tenantId: String?, role: String): User {
        val user = User(username = username, tenantId = tenantId, role = role)
        return userRepository.save(user)
    }

    fun updateUser(id: Long, username: String, tenantId: String?, role: String): User {
        val existing = userRepository.findById(id).orElseThrow { NoSuchElementException("User not found: $id") }
        val updated = existing.copy(username = username, tenantId = tenantId, role = role)
        return userRepository.save(updated)
    }

    fun deleteUser(id: Long) {
        userRepository.deleteById(id)
    }

    fun findAll(): List<User> = userRepository.findAll().toList()

    fun findByTenantId(tenantId: String): List<User> = userRepository.findByTenantId(tenantId)

    fun findById(id: Long): User? = userRepository.findById(id).orElse(null)
}
