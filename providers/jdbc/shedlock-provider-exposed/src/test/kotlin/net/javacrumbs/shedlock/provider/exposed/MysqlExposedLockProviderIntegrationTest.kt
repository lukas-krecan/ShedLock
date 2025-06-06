package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.MySqlConfig

class MysqlExposedLockProviderIntegrationTest : AbstractExposedLockProviderIntegrationTest(dbConfig = DB_CONFIG) {

    private companion object {
        private val DB_CONFIG =
            object : MySqlConfig() {
                override fun nowExpression(): String = "current_timestamp(6)"
            }
    }
}
