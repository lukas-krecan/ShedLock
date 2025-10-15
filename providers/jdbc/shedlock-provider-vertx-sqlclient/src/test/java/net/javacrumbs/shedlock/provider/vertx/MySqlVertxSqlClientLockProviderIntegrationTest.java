package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.mysqlclient.MySQLBuilder;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import java.net.URI;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.MySqlConfig;

public class MySqlVertxSqlClientLockProviderIntegrationTest extends AbstractVertxSqlClientLockProviderIntegrationTest {

    public MySqlVertxSqlClientLockProviderIntegrationTest() {
        super(new MySqlConfig());
    }

    @Override
    protected DatabaseProduct databaseProduct() {
        return DatabaseProduct.MY_SQL;
    }

    @Override
    protected SqlClient createPool(DbConfig cfg) {
        URI uri = getDbUri(cfg);

        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setDatabase(getDb(uri))
                .setUser(cfg.getUsername())
                .setPassword(cfg.getPassword());

        // Pool options
        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);

        // Create the client pool
        return MySQLBuilder.client()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .build();
    }
}
