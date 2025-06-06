package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.DbConfig
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig

class PostgresExposedLockProviderIntegrationTest : AbstractExposedLockProviderIntegrationTest(dbConfig = DB_CONFIG) {
    private companion object {
        private val DB_CONFIG: DbConfig =
            object : PostgresConfig() {
                override fun nowExpression(): String {
                    return "CURRENT_TIMESTAMP"
                }
            }
    }
}
