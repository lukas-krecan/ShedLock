package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.sqlclient.SqlClient;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
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

    @SuppressWarnings("NullAway.Init")
    private SqlClient sqlClient;

    protected AbstractVertxSqlClientLockProviderIntegrationTest(DbConfig dbConfig) {
        this.dbConfig = dbConfig;
    }

    @BeforeAll
    public void startDb() {
        dbConfig.startDb();
        sqlClient = createPool(dbConfig);
    }

    @AfterAll
    public void stopDb() {
        sqlClient.close();
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
            return new VertxSqlClientLockProvider(
                    VertxSqlClientLockProvider.Configuration.builder(sqlClient, databaseProduct())
                            .build());
        }

        @Override
        protected boolean useDbTime() {
            return false;
        }
    }

    protected abstract DatabaseProduct databaseProduct();

    @Nested
    class DbTime extends AbstractJdbcLockProviderIntegrationTest {
        @Override
        protected DbConfig getDbConfig() {
            return dbConfig;
        }

        @Override
        protected StorageBasedLockProvider getLockProvider() {
            return new VertxSqlClientLockProvider(
                    VertxSqlClientLockProvider.Configuration.builder(sqlClient, databaseProduct())
                            .usingDbTime()
                            .build());
        }

        @Override
        protected boolean useDbTime() {
            return true;
        }
    }

    protected abstract SqlClient createPool(DbConfig cfg);
}
