package net.javacrumbs.shedlock.test.support.jdbc;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

abstract class AbstractDbConfig implements DbConfig {
    private HikariDataSource dataSource;

    @Override
    public synchronized DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public final void startDb() {
        doStartDb();
        HikariDataSource newDataSource = new HikariDataSource();
        newDataSource.setJdbcUrl(getJdbcUrl());
        newDataSource.setUsername(getUsername());
        newDataSource.setPassword(getPassword());
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
}
