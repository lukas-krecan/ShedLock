package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.MsSqlServerConfig
import org.jetbrains.exposed.sql.Database

class MsSqlExposedLockProviderIntegrationTest :
    AbstractExposedLockProviderIntegrationTest(
        dvConfig = DB_CONFIG,
        database = Database.connect(DB_CONFIG.dataSource),
    ) {

    private companion object {
        private val DB_CONFIG = MsSqlServerConfig()
    }
}
