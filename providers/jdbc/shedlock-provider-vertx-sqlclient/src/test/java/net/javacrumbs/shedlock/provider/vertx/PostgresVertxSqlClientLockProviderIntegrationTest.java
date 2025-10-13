package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.net.URI;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractJdbcLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PostgresVertxSqlClientLockProviderIntegrationTest {
    private static final PostgresConfig dbConfig = new PostgresConfig();
    private Pool pool;

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

    private static Pool createPool(DbConfig cfg) {
        String jdbcUrl = cfg.getJdbcUrl();
        // Expected format: jdbc:postgresql://host:port/database[?params]
        String url = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
        URI uri = URI.create(url);

        String db = uri.getPath();
        if (db != null && db.startsWith("/")) {
            db = db.substring(1);
        }

        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setDatabase(db)
                .setUser(cfg.getUsername())
                .setPassword(cfg.getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        return PgPool.pool(connectOptions, poolOptions);
    }
}
