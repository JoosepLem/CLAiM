package ee.claimai.tenant

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class TenantSchemaTest {

    @AfterEach
    fun tearDown() {
        TenantContext.clear()
    }

    @Test
    fun `qualify prefixes table name with tenant schema`() {
        TenantContext.set("tenant_x")
        assertThat(TenantSchema.qualify("treatment_invoices"))
            .isEqualTo("tenant_x.treatment_invoices")
    }

    @Test
    fun `qualify throws when no tenant is set`() {
        assertThatThrownBy { TenantSchema.qualify("invoices") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("TenantContext")
    }
}
