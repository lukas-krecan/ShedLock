/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
