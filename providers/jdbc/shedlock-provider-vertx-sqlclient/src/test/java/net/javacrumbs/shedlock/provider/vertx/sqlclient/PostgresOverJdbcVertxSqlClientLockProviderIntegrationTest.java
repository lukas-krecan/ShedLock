package net.javacrumbs.shedlock.provider.vertx.sqlclient;

import io.vertx.core.Vertx;
import io.vertx.jdbcclient.JDBCConnectOptions;
import io.vertx.jdbcclient.JDBCPool;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;

public class PostgresOverJdbcVertxSqlClientLockProviderIntegrationTest
        extends AbstractVertxSqlClientLockProviderIntegrationTest {

    public PostgresOverJdbcVertxSqlClientLockProviderIntegrationTest() {
        super(new PostgresConfig());
    }

    @Override
    protected DatabaseProduct databaseProduct() {
        return DatabaseProduct.POSTGRES_SQL;
    }

    @Override
    protected SqlClient createPool(DbConfig cfg) {
        JDBCConnectOptions connectOptions = new JDBCConnectOptions()
                .setJdbcUrl(cfg.getJdbcUrl())
                .setUser(cfg.getUsername())
                .setPassword(cfg.getPassword());
        PoolOptions poolOptions = new PoolOptions().setMaxSize(16);
        return JDBCPool.pool(Vertx.builder().build(), connectOptions, poolOptions);
    }
}
