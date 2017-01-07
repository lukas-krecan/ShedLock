package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractMySqlJdbcLockProviderIntegrationTest;

public class MySqlJdbcLockProviderIntegrationTest extends AbstractMySqlJdbcLockProviderIntegrationTest {
    @Override
    protected LockProvider getLockProvider() {
        return new JdbcLockProvider(testUtils.getDatasource(), "shedlock");
    }
}