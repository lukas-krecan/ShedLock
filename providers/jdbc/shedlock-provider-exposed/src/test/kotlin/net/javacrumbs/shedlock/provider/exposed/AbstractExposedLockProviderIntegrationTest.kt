package net.javacrumbs.shedlock.provider.exposed

import net.javacrumbs.shedlock.support.StorageBasedLockProvider
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestInstance.Lifecycle.PER_CLASS

@TestInstance(PER_CLASS)
abstract class AbstractExposedLockProviderIntegrationTest(
    private val dbConfig: DbConfig,
    private val database: Database = Database.connect(dbConfig.dataSource),
) : AbstractJdbcLockProviderIntegrationTest() {

    override fun getDbConfig(): DbConfig = dbConfig

    override fun useDbTime(): Boolean = true

    override fun getLockProvider(): StorageBasedLockProvider = ExposedLockProvider(database)

    @BeforeAll
    fun startDb() {
        dbConfig.startDb()
    }

    @AfterAll
    fun shutdownDb() {
        dbConfig.shutdownDb()
    }
}
