package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractJdbcTest {
    private final DbConfig dbConfig;

    public AbstractJdbcTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Nested
    class ClientTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new JdbcLockProvider(
                    JdbcLockProvider.Configuration.builder(getDatasource()).build());
        }

        @Override
        protected boolean useDbTime() {
            return false;
        }
    }

    @Nested
    class DbTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new JdbcLockProvider(JdbcLockProvider.Configuration.builder(getDatasource())
                    .usingDbTime()
                    .build());
        }

        @Override
        protected boolean useDbTime() {
            return true;
        }
    }

    @BeforeAll
    public void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public void shutDownDb() {
        dbConfig.shutdownDb();
    }
}
