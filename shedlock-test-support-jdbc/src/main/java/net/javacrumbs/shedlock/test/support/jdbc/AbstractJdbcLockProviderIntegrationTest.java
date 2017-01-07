package net.javacrumbs.shedlock.test.support.jdbc;

import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
}