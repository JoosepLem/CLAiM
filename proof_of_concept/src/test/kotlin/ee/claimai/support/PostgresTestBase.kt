package ee.claimai.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
abstract class PostgresTestBase {

    companion object {
        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("claim")
            .withUsername("claim")
            .withPassword("claim")
            .apply { start() }

        @JvmStatic
        @DynamicPropertySource
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.hikari.jdbc-url") { postgres.jdbcUrl }
            registry.add("spring.datasource.hikari.username") { postgres.username }
            registry.add("spring.datasource.hikari.password") { postgres.password }
            registry.add("app.datasource.migration.jdbc-url") { postgres.jdbcUrl }
            registry.add("app.datasource.migration.username") { postgres.username }
            registry.add("app.datasource.migration.password") { postgres.password }
        }
    }
}
