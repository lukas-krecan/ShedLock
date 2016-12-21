package net.javacrumbs.shedlock.provider.jdbctemplate;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public class MySqlJdbcTemplateLockProviderTest extends AbstractJdbcTemplateLockProviderTest {
    private static final MySqlConfig dbConfig = new MySqlConfig();

    @BeforeClass
    public static void startMySql() {
        dbConfig.startDb();
    }

    @AfterClass
    public static void shutDownMysql() {
        dbConfig.shutdownDb();
    }

    @Override
    protected DbConfig getDbConfig() {
        return dbConfig;
    }
}