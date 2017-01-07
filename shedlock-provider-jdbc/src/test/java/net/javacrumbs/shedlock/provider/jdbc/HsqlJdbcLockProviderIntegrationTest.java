package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractHsqlJdbcLockProviderIntegrationTest;

public class HsqlJdbcLockProviderIntegrationTest extends AbstractHsqlJdbcLockProviderIntegrationTest {

    @Override
    protected LockProvider getLockProvider() {
        return new JdbcLockProvider(testUtils.getDatasource(), "shedlock");
    }
}