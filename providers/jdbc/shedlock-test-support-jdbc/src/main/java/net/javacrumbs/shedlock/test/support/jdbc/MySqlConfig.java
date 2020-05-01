/**
 * Copyright 2009-2020 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;

class MySqlConfig implements DbConfig {

    private static final String TEST_SCHEMA_NAME = "shedlock_test";
    private static final Logger logger = LoggerFactory.getLogger(PostgresConfig.class);
    private MyMySQLContainer mysql;

    public void startDb() {
        mysql = new MyMySQLContainer()
            .withDatabaseName(TEST_SCHEMA_NAME)
            .withUsername("SA")
            .withPassword("pass")
            .withLogConsumer(outputFrame -> logger.debug(outputFrame.getUtf8String()));
        mysql.start();
    }

    public void shutdownDb() {
        mysql.stop();
    }

    public String getJdbcUrl() {
        return mysql.getJdbcUrl();
    }

    @Override
    public String getUsername() {
        return mysql.getUsername();
    }

    @Override
    public String getPassword() {
        return mysql.getPassword();
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP(3) NOT NULL, locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name))";
    }

    private static class MyMySQLContainer extends MySQLContainer<MyMySQLContainer> {
    }
}
