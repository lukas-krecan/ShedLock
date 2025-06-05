package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.MySqlConfig
import org.jetbrains.exposed.sql.Database

class MysqlExposedLockProviderIntegrationTest : AbstractExposedLockProviderIntegrationTest(
        dvConfig = DB_CONFIG,
        database = Database.connect(DB_CONFIG.dataSource)
) {

    private companion object {
        private val DB_CONFIG = object : MySqlConfig() {
            override fun nowExpression(): String = "current_timestamp(6)"
        };
    }
}
