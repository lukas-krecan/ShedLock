package net.javacrumbs.shedlock.provider.jdbctemplate;

public class HsqlJdbcTemplateFuzzTest extends AbstractJdbcTemplateFuzzTest {
    private static final HsqlConfig dbConfig = new HsqlConfig();

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }
}