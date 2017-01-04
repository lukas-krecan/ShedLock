package net.javacrumbs.shedlock.provider.jdbc;

public class HsqlJdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }
}