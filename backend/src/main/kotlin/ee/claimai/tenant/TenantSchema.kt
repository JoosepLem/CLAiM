package ee.claimai.tenant

object TenantSchema {
    fun qualify(table: String): String {
        val tenant = TenantContext.get()
            ?: throw IllegalStateException("TenantContext not set — call TenantContext.set() first")
        return "$tenant.$table"
    }
}
