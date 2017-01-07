package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractMySqlJdbcLockProviderIntegrationTest;

public class MySqlJdbcTemplateLockProviderIntegrationTest extends AbstractMySqlJdbcLockProviderIntegrationTest {
    @Override
    protected LockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(testUtils.getDatasource(), "shedlock");
    }
}