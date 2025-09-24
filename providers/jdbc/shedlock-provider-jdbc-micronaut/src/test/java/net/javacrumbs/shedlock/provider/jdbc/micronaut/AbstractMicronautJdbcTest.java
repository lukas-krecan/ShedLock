package net.javacrumbs.shedlock.provider.jdbc.micronaut;

import io.micronaut.data.spring.jdbc.SpringJdbcConnectionOperations;
import io.micronaut.transaction.jdbc.DataSourceTransactionManager;
import net.javacrumbs.shedlock.provider.jdbc.micronaut.MicronautJdbcLockProvider.Configuration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractMicronautJdbcTest {
    private final DbConfig dbConfig;

    public AbstractMicronautJdbcTest(DbConfig dbConfig) {
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
            return new MicronautJdbcLockProvider(Configuration.builder(new DataSourceTransactionManager(
                            getDatasource(), new SpringJdbcConnectionOperations(getDatasource()), null))
                    .build());
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
            return new MicronautJdbcLockProvider(Configuration.builder(new DataSourceTransactionManager(
                            getDatasource(), new SpringJdbcConnectionOperations(getDatasource()), null))
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
