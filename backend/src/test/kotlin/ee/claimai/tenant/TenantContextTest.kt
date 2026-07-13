package ee.claimai.tenant

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference

class TenantContextTest {

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `get returns null when context is not set`() {
        assertThat(TenantContext.get()).isNull()
    }

    @Test
    fun `set and get returns the same tenant id`() {
        TenantContext.set("tenant_x")
        assertThat(TenantContext.get()).isEqualTo("tenant_x")
    }

    @Test
    fun `clear removes the tenant id`() {
        TenantContext.set("tenant_x")
        TenantContext.clear()
        assertThat(TenantContext.get()).isNull()
    }

    @Test
    fun `context is thread local and isolated between threads`() {
        TenantContext.set("main_tenant")
        val otherTenant = AtomicReference<String?>()
        val thread = Thread {
            TenantContext.set("thread_tenant")
            otherTenant.set(TenantContext.get())
        }
        thread.start()
        thread.join()

        assertThat(TenantContext.get()).isEqualTo("main_tenant")
        assertThat(otherTenant.get()).isEqualTo("thread_tenant")
    }
}
