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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ExecutionException;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

public abstract class AbstractJdbcLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    protected JdbcTestUtils testUtils;

    @BeforeEach
    public void initTestUtils() {
        testUtils = new JdbcTestUtils(getDbConfig());
    }

    protected abstract DbConfig getDbConfig();

    protected abstract boolean useDbTime();

    @AfterEach
    public void cleanup() {
        testUtils.clean();
    }

    @Override
    protected void assertUnlocked(String lockName) {
        JdbcTestUtils.LockInfo lockInfo = getLockInfo(lockName);
        Instant now = useDbTime() ? lockInfo.getDbTime() : ClockProvider.now();
        assertThat(lockInfo.getLockUntil())
                .describedAs("is unlocked")
                .isBeforeOrEqualTo(now.truncatedTo(ChronoUnit.MILLIS).plusMillis(1));
    }

    @Override
    protected void assertLocked(String lockName) {
        JdbcTestUtils.LockInfo lockInfo = getLockInfo(lockName);
        Instant now = useDbTime() ? lockInfo.getDbTime() : ClockProvider.now();

        assertThat(lockInfo.getLockUntil())
                .describedAs(getClass().getName() + " is locked")
                .isAfter(now);
    }

    @Test
    public void shouldCreateLockIfRecordAlreadyExists() {
        Timestamp previousLockTime = Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS));
        testUtils
                .getJdbcTemplate()
                .update(
                        "INSERT INTO shedlock(name, lock_until, locked_at, locked_by) VALUES(?, ?, ?, ?)",
                        LOCK_NAME1,
                        previousLockTime,
                        previousLockTime,
                        "me");
        assertUnlocked(LOCK_NAME1);
        shouldCreateLock();
    }

    @Test
    public void fuzzTestShouldWorkWithTransaction() throws ExecutionException, InterruptedException {
        TransactionalFuzzTester.fuzzTestShouldWorkWithTransaction(getLockProvider(), getDatasource());
    }

    @Test
    @EnabledIf("failIfKeyNameTooLong")
    public void shouldFailIfKeyNameTooLong() {
        LockConfiguration configuration = lockConfig(
                "lock name that is too long Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua.");
        assertThatThrownBy(() -> getLockProvider().lock(configuration)).isInstanceOf(LockException.class);
    }

    @SuppressWarnings("UnusedMethod")
    private boolean failIfKeyNameTooLong() {
        return getDbConfig().failIfKeyNameTooLong(useDbTime());
    }

    protected DataSource getDatasource() {
        return testUtils.getDatasource();
    }

    protected JdbcTestUtils.LockInfo getLockInfo(String lockName) {
        return testUtils.getLockInfo(lockName);
    }
}
