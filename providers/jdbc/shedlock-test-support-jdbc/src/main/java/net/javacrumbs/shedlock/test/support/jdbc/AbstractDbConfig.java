package net.javacrumbs.shedlock.test.support.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

abstract class AbstractDbConfig implements DbConfig {
    private HikariDataSource dataSource;

    protected static final String TEST_SCHEMA_NAME = "shedlock_test";
    private Integer transactionIsolation;

    @Override
    public DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public final void startDb() {
        doStartDb();
        HikariDataSource newDataSource = new HikariDataSource();
        newDataSource.setJdbcUrl(getJdbcUrl());
        newDataSource.setUsername(getUsername());
        newDataSource.setPassword(getPassword());
        if (transactionIsolation != null) {
            newDataSource.setTransactionIsolation(String.valueOf(transactionIsolation));
        }
        dataSource = newDataSource;
    }

    protected void doStartDb() {

    }

    @Override
    public final void shutdownDb() {
        dataSource.close();
        doShutdownDb();
    }

    protected void doShutdownDb() {

    }

    public void setTransactionIsolation(int transactionIsolation) {
        this.transactionIsolation = transactionIsolation;
    }
}
