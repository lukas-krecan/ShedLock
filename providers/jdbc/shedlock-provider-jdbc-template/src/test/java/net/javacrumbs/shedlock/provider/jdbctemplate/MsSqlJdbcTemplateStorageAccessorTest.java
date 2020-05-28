package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.test.support.jdbc.MsSqlServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class MsSqlJdbcTemplateStorageAccessorTest extends AbstractJdbcTemplateStorageAccessorTest {

    private static final MsSqlServerConfig dbConfig = new MsSqlServerConfig();

    protected MsSqlJdbcTemplateStorageAccessorTest() {
        super(dbConfig);
    }

    @BeforeAll
    public static void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public static void shutdownDb() {
        dbConfig.shutdownDb();
    }

}
