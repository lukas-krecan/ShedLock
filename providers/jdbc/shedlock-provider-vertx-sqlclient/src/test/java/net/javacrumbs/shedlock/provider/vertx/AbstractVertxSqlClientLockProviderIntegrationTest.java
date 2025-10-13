package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.sqlclient.Pool;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractVertxSqlClientLockProviderIntegrationTest {
    private final DbConfig dbConfig;
    private Pool pool;

    protected AbstractVertxSqlClientLockProviderIntegrationTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @BeforeAll
    public void startDb() {
        dbConfig.startDb();
        pool = createPool(dbConfig);
    }

    @AfterAll
    public void stopDb() {
        if (pool != null) {
            pool.close();
        }
        dbConfig.shutdownDb();
    }

    @Nested
    class ClientTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new VertxSqlClientLockProvider(pool);
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
            return new VertxSqlClientLockProvider(VertxSqlClientLockProvider.Configuration.builder(pool)
                    .usingDbTime()
                    .build());
        }

        @Override
        protected boolean useDbTime() {
            return true;
        }
    }

    protected abstract Pool createPool(DbConfig cfg);
}
