package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.test.support.jdbc.JdbcTestUtils;
import net.javacrumbs.shedlock.test.support.jdbc.PostgresConfig;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

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
    void resetTime() {
        ClockProvider.setClock(Clock.systemDefaultZone());
    }

    @AfterEach
    public void cleanup() {
        testUtils.clean();
    }

    @Test
    void shouldUpdateOnInsertAfterValidityOfPreviousEnded() {
        JdbcTemplateStorageAccessor accessor = getAccessor();

        Instant startTime = Instant.parse("2020-04-11T05:30:00Z");
        setClock(startTime);

        Instant otherLockValidity = startTime.plusSeconds(5);
        accessor.insertRecord(new LockConfiguration("other", otherLockValidity));

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, startTime.plusSeconds(10)))
        ).isEqualTo(true);

        setClock(startTime.plusSeconds(15));

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, startTime.plusSeconds(20)))
        ).isEqualTo(true);

        // check that the other lock has not been affected by "my-lock" update
        assertThat(testUtils.getLockedUntil("other")).isEqualTo(otherLockValidity);
    }

    @Test
    void shouldNotUpdateOnInsertIfPreviousDidNotEnd() {
        JdbcTemplateStorageAccessor accessor = getAccessor();

        setClock(startTime);

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, startTime.plusSeconds(10)))
        ).isEqualTo(true);

        assertThat(
            accessor.insertRecord(new LockConfiguration(MY_LOCK, startTime.plusSeconds(5)))
        ).isEqualTo(false);

        assertThat(testUtils.getLockedUntil(MY_LOCK)).isEqualTo(startTime.plusSeconds(10));
    }

    private void setClock(Instant time) {
        ClockProvider.setClock(Clock.fixed(time, ZoneId.of("UTC")));
    }

    @NotNull
    private JdbcTemplateStorageAccessor getAccessor() {
        return new JdbcTemplateStorageAccessor(JdbcTemplateLockProvider
            .Configuration.builder()
            .withJdbcTemplate(testUtils.getJdbcTemplate())
            .build()
        );
    }

}
