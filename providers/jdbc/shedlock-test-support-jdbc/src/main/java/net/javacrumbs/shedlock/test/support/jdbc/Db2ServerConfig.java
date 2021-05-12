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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Db2Container;

public final class Db2ServerConfig extends AbstractDbConfig {

    private Db2Container db2;
    private static final Logger logger = LoggerFactory.getLogger(Db2ServerConfig.class);

    @Override
    protected void doStartDb() {
        db2 = new Db2Container()
            .acceptLicense()
            .withLogConsumer(outputFrame -> logger.debug(outputFrame.getUtf8String()));
        db2.start();
    }

    @Override
    protected void doShutdownDb() {
        db2.stop();
    }

    @Override
    public String getJdbcUrl() {
        return db2.getJdbcUrl();

    }

    @Override
    public String getUsername() {
        return db2.getUsername();
    }

    @Override
    public String getPassword() {
        return db2.getPassword();
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64) NOT NULL PRIMARY KEY, lock_until TIMESTAMP NOT NULL, locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL)";
    }

    @Override
    public String nowExpression() {
        return "(CURRENT TIMESTAMP - CURRENT TIMEZONE)";
    }
}
