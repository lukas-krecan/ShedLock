/**
 * Copyright 2009-2017 the original author or authors.
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

import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    protected JdbcTestUtils testUtils;

    @Before
    public void initTestUtils() throws SQLException {
        testUtils = new JdbcTestUtils(getDbConfig());
    }

    protected abstract DbConfig getDbConfig();

    @After
    public void cleanup() {
        testUtils.clean();
    }


    @Override
    protected void assertUnlocked(String lockName) {
        List<Map<String, Object>> unlockedRows = testUtils.getJdbcTemplate().queryForList("SELECT * FROM shedlock WHERE name = ? AND lock_until <= ?", lockName, now());
        assertThat(unlockedRows).hasSize(1);
    }

    @Override
    protected void assertLocked(String lockName) {
        List<Map<String, Object>> lockedRows = testUtils.getJdbcTemplate().queryForList("SELECT * FROM shedlock WHERE name = ? AND lock_until > ?", lockName, now());
        assertThat(lockedRows).hasSize(1);
    }

    @Test
    public void shouldCreateLockIfRecordAlreadyExists() {
        Date now = new Date();
        testUtils.getJdbcTemplate().update("INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)", LOCK_NAME1, now, now, "me");
        shouldCreateLock();
    }

    private Date now() {
        return new Date();
    }

    protected DataSource getDatasource() {
        return testUtils.getDatasource();
    }
}