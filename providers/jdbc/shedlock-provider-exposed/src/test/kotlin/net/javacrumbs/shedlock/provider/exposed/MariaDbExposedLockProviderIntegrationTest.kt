package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.MariaDbConfig

class MariaDbExposedLockProviderIntegrationTest : AbstractExposedLockProviderIntegrationTest(dbConfig = DB_CONFIG) {

    private companion object {
        private val DB_CONFIG =
            object : MariaDbConfig() {
                override fun nowExpression(): String = "current_timestamp(6)"
            }
    }
}
