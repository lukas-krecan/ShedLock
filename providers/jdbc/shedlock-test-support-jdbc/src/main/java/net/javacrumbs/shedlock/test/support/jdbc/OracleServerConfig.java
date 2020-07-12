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
import org.testcontainers.containers.OracleContainer;

public final class OracleServerConfig implements DbConfig {

    private OracleContainer oracle;
    private static final Logger logger = LoggerFactory.getLogger(OracleServerConfig.class);

    public void startDb() {
        oracle = new OracleContainer("oracleinanutshell/oracle-xe-11g")
            .withLogConsumer(outputFrame -> logger.debug(outputFrame.getUtf8String()));
        oracle.start();
    }

    public void shutdownDb() {
        oracle.stop();
    }

    public String getJdbcUrl() {
        return oracle.getJdbcUrl();

    }

    @Override
    public String getUsername() {
        return oracle.getUsername();
    }

    @Override
    public String getPassword() {
        return oracle.getPassword();
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP(3) NOT NULL, locked_at TIMESTAMP(3) NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name))";
    }

    @Override
    public String nowExpression() {
        return "SYS_EXTRACT_UTC(SYSTIMESTAMP)";
    }
}
