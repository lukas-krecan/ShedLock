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

import org.testcontainers.containers.OracleContainer;

public final class OracleServerConfig extends AbstractContainerBasedDbConfig<OracleContainer> {
    public OracleServerConfig() {
        super(new OracleContainer("gvenzl/oracle-xe:21-slim"));
    }

    @Override
    public String getCreateTableStatement() {
        return "CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP(3) NOT NULL, locked_at TIMESTAMP(3) NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name))";
    }

    @Override
    public String nowExpression() {
        return "SYS_EXTRACT_UTC(SYSTIMESTAMP)";
    }

    @Override
    public String getR2dbcUrl() {
        return "r2dbc:oracle://localhost:" + container.getOraclePort() + "/" + container.getDatabaseName();
    }

}
