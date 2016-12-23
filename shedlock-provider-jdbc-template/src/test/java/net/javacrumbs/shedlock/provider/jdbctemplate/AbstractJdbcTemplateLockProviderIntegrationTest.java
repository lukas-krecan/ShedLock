package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.Before;

import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcTemplateLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private JdbcTemplateLockProvider lockProvider;
    private JdbcTestUtils testUtils;

    @Before
    public void initLockProvider() throws SQLException {
        testUtils = new JdbcTestUtils(getDbConfig());
        lockProvider = new JdbcTemplateLockProvider(testUtils.getDatasource(), "shedlock");
    }

    protected abstract DbConfig getDbConfig();

    @After
    public void cleanup() {
        testUtils.clean();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
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

    private Date now() {
        return new Date();
    }
}