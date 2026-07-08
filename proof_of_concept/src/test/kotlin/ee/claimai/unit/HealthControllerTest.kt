package ee.claimai.unit

import ee.claimai.HealthController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class HealthControllerTest {

    private val mockMvc = MockMvcBuilders.standaloneSetup(HealthController()).build()

    @Test
    fun `GET root returns OK`() {
        val result = mockMvc.perform(get("/"))
            .andExpect(status().isOk)
            .andReturn()

        assertThat(result.response.contentAsString).isEqualTo("OK")
    }
}
