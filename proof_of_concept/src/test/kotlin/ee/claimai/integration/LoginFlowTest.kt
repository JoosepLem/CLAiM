package ee.claimai.integration

import ee.claimai.support.PostgresTestBase
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginFlowTest : PostgresTestBase() {

    @LocalServerPort
    private var port: Int = 0

    private val restTemplate = RestTemplate()

    @Test
    fun `login with user_a shows dashboard with clinic name`() {
        val loginHeaders = HttpHeaders()
        loginHeaders.contentType = MediaType.APPLICATION_FORM_URLENCODED
        val loginBody = LinkedMultiValueMap<String, String>()
        loginBody.add("username", "user_a")
        val loginRequest = HttpEntity(loginBody, loginHeaders)

        val loginResponse = restTemplate.postForEntity(
            "http://localhost:$port/login", loginRequest, String::class.java
        )

        assertThat(loginResponse.statusCode.is3xxRedirection).isTrue()

        val setCookieHeader = loginResponse.headers["Set-Cookie"]?.firstOrNull()
        assertThat(setCookieHeader).isNotNull
        val jwtValue = setCookieHeader!!.split(";").first().removePrefix("jwt=")

        val dashboardHeaders = HttpHeaders()
        dashboardHeaders.add("Cookie", "jwt=$jwtValue")
        val dashboardResponse = restTemplate.exchange(
            "http://localhost:$port/dashboard",
            org.springframework.http.HttpMethod.GET,
            HttpEntity<Any>(dashboardHeaders),
            String::class.java
        )

        assertThat(dashboardResponse.statusCode.is2xxSuccessful).isTrue()
        assertThat(dashboardResponse.body).contains("Welcome,")
        assertThat(dashboardResponse.body).contains("user_a")
        assertThat(dashboardResponse.body).contains("Demo Clinic 1")
    }

    @Test
    fun `login with unknown username stays on login page`() {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        val body = LinkedMultiValueMap<String, String>()
        body.add("username", "unknown_user")
        val request = HttpEntity(body, headers)

        val response = restTemplate.postForEntity(
            "http://localhost:$port/login", request, String::class.java
        )

        assertThat(response.statusCode.is2xxSuccessful).isTrue()
        assertThat(response.body).contains("<h1>Login</h1>")
    }
}
