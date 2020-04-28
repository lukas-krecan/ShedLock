package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.JdbcTestUtils;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

class PostgresJdbcTemplateStorageAccessorTest {
    private static final PostgresConfig dbConfig = new PostgresConfig();
    public static final String MY_LOCK = "my-lock";
    private final JdbcTestUtils testUtils = new JdbcTestUtils(dbConfig);
    private final Instant startTime = Instant.parse("2020-04-11T05:30:00Z");

    @BeforeAll
    public static void startDb() {
        dbConfig.startDb();
    }

    @AfterAll
    public static void shutdownDb() {
        dbConfig.shutdownDb();
    }

    @AfterEach
    public void cleanup() {
        testUtils.clean();
    }

    @Test
    void shouldUpdateOnInsertAfterValidityOfPreviousEnded() throws InterruptedException {
        JdbcTemplateStorageAccessor accessor = getAccessor();


        accessor.insertRecord(new LockConfiguration("other", Duration.ofSeconds(5), Duration.ZERO));
        Instant otherLockValidity = testUtils.getLockedUntil("other").toInstant();

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, Duration.ofMillis(10), Duration.ZERO))
        ).isEqualTo(true);

        sleep(10);

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, Duration.ofMillis(10), Duration.ZERO))
        ).isEqualTo(true);

        // check that the other lock has not been affected by "my-lock" update
        assertThat(testUtils.getLockedUntil("other")).isEqualTo(otherLockValidity);
    }

    @Test
    void shouldNotUpdateOnInsertIfPreviousDidNotEnd() {
        JdbcTemplateStorageAccessor accessor = getAccessor();

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, Duration.ofSeconds(10), Duration.ZERO))
        ).isEqualTo(true);

        Instant originalLockValidity = testUtils.getLockedUntil(MY_LOCK).toInstant();

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, Duration.ofSeconds(10), Duration.ZERO))
        ).isEqualTo(false);

        assertThat(testUtils.getLockedUntil(MY_LOCK)).isEqualTo(originalLockValidity);
    }


    @NonNull
    private JdbcTemplateStorageAccessor getAccessor() {
        return new JdbcTemplateStorageAccessor(JdbcTemplateLockProvider
            .Configuration.builder()
            .withJdbcTemplate(testUtils.getJdbcTemplate())
            .build()
        );
    }

}
