package net.javacrumbs.shedlock.provider.vertx;

import io.vertx.mssqlclient.MSSQLBuilder;
import io.vertx.mssqlclient.MSSQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import io.vertx.sqlclient.SqlClient;
import net.javacrumbs.shedlock.provider.sql.DatabaseProduct;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.MsSqlServerConfig;

public class MsSqlVertxSqlClientLockProviderIntegrationTest extends AbstractVertxSqlClientLockProviderIntegrationTest {

    public MsSqlVertxSqlClientLockProviderIntegrationTest() {
        super(new MsSqlServerConfig());
    }

    @Override
    protected DatabaseProduct databaseProduct() {
        return DatabaseProduct.SQL_SERVER;
    }

    @Override
    protected SqlClient createPool(DbConfig cfg) {
        MSSQLConnectOptions connectOptions = new MSSQLConnectOptions()
                .setHost(cfg.getHost())
                .setPort(cfg.getPort())
                .setUser(cfg.getUsername())
                .setPassword(cfg.getPassword());

        PoolOptions poolOptions = new PoolOptions().setMaxSize(5);
        return MSSQLBuilder.pool()
                .with(poolOptions)
                .connectingTo(connectOptions)
                .build();
    }
}
