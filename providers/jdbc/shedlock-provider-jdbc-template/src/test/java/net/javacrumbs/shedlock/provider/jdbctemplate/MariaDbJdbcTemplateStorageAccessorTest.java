package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.test.support.jdbc.MariaDbConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class MariaDbJdbcTemplateStorageAccessorTest extends AbstractJdbcTemplateStorageAccessorTest {

    private static final MariaDbConfig dbConfig = new MariaDbConfig();

    protected MariaDbJdbcTemplateStorageAccessorTest() {
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
