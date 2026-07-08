package ee.claimai.e2e

import ee.claimai.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = RestTemplate()

    @Test
    fun `GET actuator health returns UP with DB component`() {
        val response = restTemplate.getForEntity("http://localhost:$port/actuator/health", Map::class.java)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)

        @Suppress("UNCHECKED_CAST")
        val body = response.body as Map<String, Any>
        assertThat(body["status"]).isEqualTo("UP")
    }
}
