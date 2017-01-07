package net.javacrumbs.shedlock.test.support.jdbc;

public abstract class AbstractHsqlJdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }
}