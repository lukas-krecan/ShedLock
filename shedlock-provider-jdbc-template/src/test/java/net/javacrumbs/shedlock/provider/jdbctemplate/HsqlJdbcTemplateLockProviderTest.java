package net.javacrumbs.shedlock.provider.jdbctemplate;

public class HsqlJdbcTemplateLockProviderTest extends AbstractJdbcTemplateLockProviderTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }
}