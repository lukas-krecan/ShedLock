package net.javacrumbs.shedlock.provider.jdbc;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.jdbc.AbstractPostgresJdbcLockProviderIntegrationTest;

public class PostgresJdbcLockProviderIntegrationTest extends AbstractPostgresJdbcLockProviderIntegrationTest {
    @Override
    protected LockProvider getLockProvider() {
        return new JdbcLockProvider(testUtils.getDatasource(), "shedlock");
    }
}