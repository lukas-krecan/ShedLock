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


import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public abstract class AbstractDb2JdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private static final Db2ServerConfig dbConfig = new Db2ServerConfig();

    @BeforeAll
    public static void startMySql() {
        dbConfig.startDb();
    }

    @AfterAll
    public static void shutDownMysql() {
        dbConfig.shutdownDb();
    }

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }

    @Test
    public void shouldCreateLockIfRecordAlreadyExists() {
        testUtils.getJdbcTemplate().update("INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES(?, CURRENT TIMESTAMP - CURRENT TIMEZONE, CURRENT TIMESTAMP - CURRENT TIMEZONE, ?)", LOCK_NAME1, "me");
        shouldCreateLock();
    }

}
