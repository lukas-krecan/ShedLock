package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.test.support.jdbc.Db2ServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class Db2JdbcTemplateStorageAccessorTest extends AbstractJdbcTemplateStorageAccessorTest {

    private static final Db2ServerConfig dbConfig = new Db2ServerConfig();

    protected Db2JdbcTemplateStorageAccessorTest() {
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
