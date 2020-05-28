package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.test.support.jdbc.MySqlConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class MySqlJdbcTemplateStorageAccessorTest extends AbstractJdbcTemplateStorageAccessorTest {

    private static final MySqlConfig dbConfig = new MySqlConfig();

    protected MySqlJdbcTemplateStorageAccessorTest() {
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
