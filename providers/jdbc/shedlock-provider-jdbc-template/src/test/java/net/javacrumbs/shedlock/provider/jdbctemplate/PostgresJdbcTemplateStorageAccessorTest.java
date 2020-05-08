package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

class PostgresJdbcTemplateStorageAccessorTest extends AbstractJdbcTemplateStorageAccessorTest {

    private static final String MY_LOCK = "my-lock";
    private static final String OTHER_LOCK = "other-lock";

    private static final PostgresConfig dbConfig = new PostgresConfig();

    protected PostgresJdbcTemplateStorageAccessorTest() {
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

    @Test
    void shouldUpdateOnInsertAfterValidityOfPreviousEndedWhenNotUsingDbTime() throws InterruptedException {
        shouldUpdateOnInsertAfterValidityOfPreviousEnded(false);
    }

    @Test
    void shouldUpdateOnInsertAfterValidityOfPreviousEndedWhenUsingDbTime() throws InterruptedException {
        shouldUpdateOnInsertAfterValidityOfPreviousEnded(true);
    }

    private void shouldUpdateOnInsertAfterValidityOfPreviousEnded(boolean usingDbTime) throws InterruptedException {
        JdbcTemplateStorageAccessor accessor = getAccessor(usingDbTime);


        accessor.insertRecord(new LockConfiguration(OTHER_LOCK, Duration.ofSeconds(5), Duration.ZERO));
        Timestamp otherLockValidity = getTestUtils().getLockedUntil(OTHER_LOCK);

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, Duration.ofMillis(10), Duration.ZERO))
        ).isEqualTo(true);

        sleep(10);

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, Duration.ofMillis(10), Duration.ZERO))
        ).isEqualTo(true);

        // check that the other lock has not been affected by "my-lock" update
        assertThat(getTestUtils().getLockedUntil(OTHER_LOCK)).isEqualTo(otherLockValidity);
    }

}
