package net.javacrumbs.shedlock.provider.vertx;

import static java.util.concurrent.TimeUnit.SECONDS;

import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlClient;
import java.time.ZoneOffset;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.JdbcTestUtils;
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

        @Override
        public JdbcTestUtils.LockInfo getLockInfo(String lockName) {
            RowSet<Row> result;
            try {
                result = sqlClient
                        .query("SELECT name, lock_until, " + dbConfig.nowExpression()
                                + " as db_time FROM shedlock WHERE name = '" + lockName + "'")
                        .execute()
                        .toCompletionStage()
                        .toCompletableFuture()
                        .get(10, SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            Row row = result.iterator().next();
            return new JdbcTestUtils.LockInfo(
                    row.getString("name"),
                    row.getLocalDateTime("lock_until").toInstant(ZoneOffset.UTC),
                    row.getLocalDateTime("db_time").toInstant(ZoneOffset.UTC));
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
