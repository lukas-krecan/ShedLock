package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.H2Config
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.core.ExperimentalKeywordApi
import org.jetbrains.exposed.v1.jdbc.Database

@OptIn(ExperimentalKeywordApi::class)
class H2ExposedLockProviderIntegrationTest :
    AbstractExposedLockProviderIntegrationTest(
        dbConfig = DB_CONFIG,
        database =
            Database.connect(DB_CONFIG.dataSource, databaseConfig = DatabaseConfig { preserveKeywordCasing = false }),
    ) {

    private companion object {
        private val DB_CONFIG = H2Config()
    }
}
