package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.MariaDbConfig
import org.jetbrains.exposed.sql.Database

class MariaDbExposedLockProviderIntegrationTest :
    AbstractExposedLockProviderIntegrationTest(
        dbConfig = DB_CONFIG,
        database = Database.connect(DB_CONFIG.dataSource),
    ) {

    private companion object {
        private val DB_CONFIG =
            object : MariaDbConfig() {
                override fun nowExpression(): String = "current_timestamp(6)"
            }
    }
}
