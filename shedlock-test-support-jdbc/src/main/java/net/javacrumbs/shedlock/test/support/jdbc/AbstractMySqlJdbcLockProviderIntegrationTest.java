package net.javacrumbs.shedlock.test.support.jdbc;

import org.junit.AfterClass;
import org.junit.BeforeClass;

public abstract class AbstractMySqlJdbcLockProviderIntegrationTest extends AbstractJdbcLockProviderIntegrationTest {
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