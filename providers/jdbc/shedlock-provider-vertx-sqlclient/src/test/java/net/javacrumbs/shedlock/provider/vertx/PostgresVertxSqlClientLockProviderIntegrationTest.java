package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.pgclient.PgBuilder;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import java.net.URI;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;

public class PostgresVertxSqlClientLockProviderIntegrationTest
        extends AbstractVertxSqlClientLockProviderIntegrationTest {

    public PostgresVertxSqlClientLockProviderIntegrationTest() {
        super(new PostgresConfig());
    }

    @Override
    protected DatabaseProduct databaseProduct() {
        return DatabaseProduct.POSTGRES_SQL;
    }

    @Override
    protected SqlClient createPool(DbConfig cfg) {
        URI uri = getDbUri(cfg);

        PgConnectOptions connectOptions = new PgConnectOptions()
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setDatabase(getDb(uri))
                .setUser(cfg.getUsername())
                .setPassword(cfg.getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        return PgBuilder.client().with(poolOptions).connectingTo(connectOptions).build();
    }
}
