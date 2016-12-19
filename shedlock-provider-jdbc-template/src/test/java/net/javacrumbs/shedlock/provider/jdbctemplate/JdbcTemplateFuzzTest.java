package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractFuzzTest;
import org.junit.After;
import org.junit.Before;

import java.sql.SQLException;

public class JdbcTemplateFuzzTest extends AbstractFuzzTest {
    private JdbcTemplateLockProvider lockProvider;
    private JdbcTestUtils testUtils;

    @Before
    public void initLockProvider() throws SQLException {
        testUtils = new JdbcTestUtils();
        lockProvider = new JdbcTemplateLockProvider(testUtils.getDatasource(), "shedlock");
    }

    @After
    public void cleanup() {
        testUtils.clean();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

}