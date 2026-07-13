package ee.claimai.tenant

object TenantContext {
    private val holder = ThreadLocal<String>()

    private val tenantPattern = Regex("^[a-z][a-z0-9_]{0,62}\$")

    fun set(tenantId: String) {
        require(tenantId.matches(tenantPattern)) {
            "Invalid tenant ID: $tenantId"
        }
        holder.set(tenantId)
    }

    fun get(): String? = holder.get()

    fun clear() {
        holder.remove()
    }
}
