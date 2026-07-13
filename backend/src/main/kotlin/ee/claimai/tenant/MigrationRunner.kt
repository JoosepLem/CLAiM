package ee.claimai.tenant

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Component
@Order(1)
class MigrationRunner(
    private val migrations: TenantMigrationService
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        migrations.migrateAll()
    }
}
