package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderTest;
import org.junit.After;
import org.junit.Before;

import java.sql.SQLException;
import java.util.Date;

public abstract class AbstractJdbcTemplateLockProviderTest extends AbstractLockProviderTest {
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
        testUtils.getJdbcTemplate().queryForList("SELECT * FROM shedlock WHERE name = ? AND lock_until <= ?", lockName, now());
    }

    @Override
    protected void assertLocked(String lockName) {
        testUtils.getJdbcTemplate().queryForList("SELECT * FROM shedlock WHERE name = ? AND lock_until > ?", lockName, now());
    }

    private Date now() {
        return new Date();
    }
}