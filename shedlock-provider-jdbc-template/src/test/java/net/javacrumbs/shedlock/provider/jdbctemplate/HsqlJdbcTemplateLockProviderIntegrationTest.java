package net.javacrumbs.shedlock.provider.jdbctemplate;

public class HsqlJdbcTemplateLockProviderIntegrationTest extends AbstractJdbcTemplateLockProviderIntegrationTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }
}