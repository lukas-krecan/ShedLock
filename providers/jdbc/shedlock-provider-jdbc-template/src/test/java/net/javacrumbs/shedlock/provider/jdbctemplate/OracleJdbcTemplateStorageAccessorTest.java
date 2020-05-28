package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.test.support.jdbc.OracleServerConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

class OracleJdbcTemplateStorageAccessorTest extends AbstractJdbcTemplateStorageAccessorTest {

    private static final OracleServerConfig dbConfig = new OracleServerConfig();

    protected OracleJdbcTemplateStorageAccessorTest() {
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
