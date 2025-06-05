package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.test.support.jdbc.H2Config
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.ExperimentalKeywordApi

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
