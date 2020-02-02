/**
 * Copyright 2009-2019 the original author or authors.
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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    protected JdbcTestUtils testUtils;

    @BeforeEach
    public void initTestUtils() {
        testUtils = new JdbcTestUtils(getDbConfig());
    }

    protected abstract DbConfig getDbConfig();

    @AfterEach
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
        Calendar now = now();
        testUtils.getJdbcTemplate().update("INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)", LOCK_NAME1, now, now, "me");
        shouldCreateLock();
    }

    @Test
    public void fuzzTestShouldWorkWithTransaction() throws ExecutionException, InterruptedException {
        TransactionalFuzzTester.fuzzTestShouldWorkWithTransaction(getLockProvider(), getDatasource());
    }

    @Test
    public void shouldNotFailIfKeyNameTooLong() {
        LockConfiguration configuration = lockConfig("lock name that is too long Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        Optional<SimpleLock> lock = getLockProvider().lock(configuration);
        assertThat(lock).isEmpty();
    }

    protected Calendar now() {
        return Calendar.getInstance();
    }

    protected DataSource getDatasource() {
        return testUtils.getDatasource();
    }
}
