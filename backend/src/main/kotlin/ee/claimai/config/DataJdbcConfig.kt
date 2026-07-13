package ee.claimai.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.relational.core.mapping.NamingStrategy
import org.springframework.data.relational.core.mapping.RelationalPersistentProperty

@Configuration
class DataJdbcConfig {

    @Bean
    fun namingStrategy(): NamingStrategy {
        val camelPattern = Regex("([a-z0-9])([A-Z])")
        return object : NamingStrategy {
            override fun getColumnName(property: RelationalPersistentProperty): String {
                return camelPattern.replace(property.name) { "${it.groupValues[1]}_${it.groupValues[2]}" }.lowercase()
            }

            override fun getReverseColumnName(property: RelationalPersistentProperty): String {
                return getColumnName(property)
            }

            override fun getKeyColumn(property: RelationalPersistentProperty): String {
                return getColumnName(property)
            }
        }
    }
}
