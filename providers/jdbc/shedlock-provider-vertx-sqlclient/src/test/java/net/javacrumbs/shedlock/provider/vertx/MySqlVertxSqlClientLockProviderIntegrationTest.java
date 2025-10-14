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

    protected SqlClient createPool(DbConfig cfg) {
        String jdbcUrl = cfg.getJdbcUrl();
        // Expected format: jdbc:postgresql://host:port/database[?params]
        String url = jdbcUrl.startsWith("jdbc:") ? jdbcUrl.substring(5) : jdbcUrl;
        URI uri = URI.create(url);

        String db = uri.getPath();
        if (db != null && db.startsWith("/")) {
            db = db.substring(1);
        }

        MySQLConnectOptions connectOptions = new MySQLConnectOptions()
                .setPort(3306)
                .setHost(uri.getHost())
                .setPort(uri.getPort())
                .setDatabase(db)
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
