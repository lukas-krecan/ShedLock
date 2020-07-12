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
import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgresConfig implements DbConfig {

    private static final String TEST_SCHEMA_NAME = "shedlock_test";
    private MyPostgreSQLContainer postgres;
    private static final Logger logger = LoggerFactory.getLogger(PostgresConfig.class);

    public void startDb() {
        postgres = new MyPostgreSQLContainer()
            .withDatabaseName(TEST_SCHEMA_NAME)
            .withUsername("SA")
            .withPassword("pass")
            .withLogConsumer(outputFrame -> logger.debug(outputFrame.getUtf8String()));
        postgres.start();
    }

    public void shutdownDb() {
        postgres.stop();
    }

    public String getJdbcUrl() {
        return postgres.getJdbcUrl();

    }

    @Override
    public String getUsername() {
        return postgres.getUsername();
    }

    @Override
    public String getPassword() {
        return postgres.getPassword();
    }

    @Override
    public String nowExpression() {
        return "timezone('utc', CURRENT_TIMESTAMP)";
    }

    private static class MyPostgreSQLContainer extends PostgreSQLContainer<MyPostgreSQLContainer> {
    }
}
