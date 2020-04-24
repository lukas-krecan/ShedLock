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
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;

public final class MsSqlServerConfig implements DbConfig {

    private MyMSSQLServerContainer mssql;
    private static final Logger logger = LoggerFactory.getLogger(MsSqlServerConfig.class);

    public void startDb() {
        mssql = new MyMSSQLServerContainer()
            .withLogConsumer(new Consumer<OutputFrame>() {
                @Override
                public void accept(OutputFrame outputFrame) {
                    logger.debug(outputFrame.getUtf8String());
                }
            });
        mssql.start();
    }

    public void shutdownDb() {
        mssql.stop();
    }

    public String getJdbcUrl() {
        return mssql.getJdbcUrl();

    }

    @Override
    public String getUsername() {
        return mssql.getUsername();
    }

    @Override
    public String getPassword() {
        return mssql.getPassword();
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64), lock_until datetime, locked_at datetime, locked_by VARCHAR(255), PRIMARY KEY (name))";
    }

    private static class MyMSSQLServerContainer extends MSSQLServerContainer<MyMSSQLServerContainer> {
    }
}
