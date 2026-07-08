package ee.claimai.user

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class UserRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findByUsername(username: String): Map<String, Any>? {
        val rows = jdbcTemplate.queryForList(
            "SELECT username, tenant_id FROM public.users WHERE username = ?", username
        )
        val row = rows.firstOrNull() ?: return null
        return mapOf("username" to row["username"]!!, "tenant_id" to row["tenant_id"]!!)
    }
}
