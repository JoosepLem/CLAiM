package ee.claimai.config

import com.zaxxer.hikari.HikariDataSource
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import javax.sql.DataSource

@Configuration
class DataSourceConfig {

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    fun dataSource(): HikariDataSource {
        val ds = HikariDataSource()
        ds.jdbcUrl = "jdbc:postgresql://localhost:5432/claim"
        ds.username = "claim"
        ds.password = "claim"
        return ds
    }

    @Bean
    @ConfigurationProperties("app.datasource.migration")
    fun migrationDataSource(): HikariDataSource {
        val ds = HikariDataSource()
        ds.jdbcUrl = "jdbc:postgresql://localhost:5432/claim"
        ds.username = "claim"
        ds.password = "claim"
        ds.maximumPoolSize = 2
        return ds
    }
}
