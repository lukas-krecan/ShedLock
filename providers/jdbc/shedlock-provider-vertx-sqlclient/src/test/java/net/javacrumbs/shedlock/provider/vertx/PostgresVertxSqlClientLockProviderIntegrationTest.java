package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import java.net.URI;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;

public class PostgresVertxSqlClientLockProviderIntegrationTest
        extends AbstractVertxSqlClientLockProviderIntegrationTest {

    public PostgresVertxSqlClientLockProviderIntegrationTest() {
        super(new PostgresConfig());
    }

    protected Pool createPool(DbConfig cfg) {
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
