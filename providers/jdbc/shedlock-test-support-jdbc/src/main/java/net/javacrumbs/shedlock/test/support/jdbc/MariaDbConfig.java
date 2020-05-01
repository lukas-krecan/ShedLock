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
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;

class MariaDbConfig implements DbConfig {

    private static final String TEST_SCHEMA_NAME = "shedlock_test";
    private static final Logger logger = LoggerFactory.getLogger(PostgresConfig.class);
    private MyMariaDbContainer mariaDb;

    public void startDb() {
        mariaDb = new MyMariaDbContainer()
            .withDatabaseName(TEST_SCHEMA_NAME)
            .withUsername("SA")
            .withPassword("pass")
            .withLogConsumer(outputFrame -> logger.debug(outputFrame.getUtf8String()));
        mariaDb.start();
    }

    public void shutdownDb() {
        mariaDb.stop();
    }

    public String getJdbcUrl() {
        return mariaDb.getJdbcUrl();
    }

    @Override
    public String getUsername() {
        return mariaDb.getUsername();
    }

    @Override
    public String getPassword() {
        return mariaDb.getPassword();
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP NOT NULL, locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name))";
    }

    private static class MyMariaDbContainer extends MariaDBContainer<MyMariaDbContainer> {
    }
}
