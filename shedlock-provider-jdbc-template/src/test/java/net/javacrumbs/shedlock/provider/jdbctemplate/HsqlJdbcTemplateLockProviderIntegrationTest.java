package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractHsqlJdbcLockProviderIntegrationTest;

public class HsqlJdbcTemplateLockProviderIntegrationTest extends AbstractHsqlJdbcLockProviderIntegrationTest {

    @Override
    protected LockProvider getLockProvider() {
        return new JdbcTemplateLockProvider(testUtils.getDatasource(), "shedlock");
    }
}