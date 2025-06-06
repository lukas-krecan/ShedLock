package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.MsSqlServerConfig

class MsSqlExposedLockProviderIntegrationTest : AbstractExposedLockProviderIntegrationTest(dbConfig = DB_CONFIG) {

    private companion object {
        private val DB_CONFIG = MsSqlServerConfig()
    }
}
