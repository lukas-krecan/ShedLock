/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.test.support.jdbc;

import static org.testcontainers.mssqlserver.MSSQLServerContainer.MS_SQL_SERVER_PORT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

public final class MsSqlServerConfig extends AbstractContainerBasedDbConfig<MSSQLServerContainer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MsSqlServerConfig.class);

    public MsSqlServerConfig() {
        super(new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
                .withLogConsumer(it -> LOGGER.info(it.getUtf8String()))
                .acceptLicense());
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until datetime2 NOT NULL, locked_at datetime2 NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name))";
    }

    @Override
    public String getR2dbcUrl() {
        return "r2dbc:sqlserver://" + container.getHost() + ":" + container.getMappedPort(MS_SQL_SERVER_PORT);
    }

    @Override
    public String nowExpression() {
        return "SYSUTCDATETIME()";
    }
}
