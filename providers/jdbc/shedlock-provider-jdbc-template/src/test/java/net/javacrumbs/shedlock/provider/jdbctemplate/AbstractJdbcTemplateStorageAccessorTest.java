package net.javacrumbs.shedlock.provider.jdbctemplate;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.JdbcTestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Duration;

import static java.lang.Thread.sleep;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractJdbcTemplateStorageAccessorTest {

    private static final String MY_LOCK = "my-lock";
    private static final String OTHER_LOCK = "other-lock";

    private final JdbcTestUtils testUtils;

    protected AbstractJdbcTemplateStorageAccessorTest(DbConfig dbConfig) {
        this.testUtils = new JdbcTestUtils(dbConfig);
    }

    @AfterEach
    public void cleanup() {
        testUtils.clean();
    }

    @Test
    void shouldNotUpdateOnInsertIfPreviousDidNotEndWhenNotUsingDbTime() {
        shouldNotUpdateOnInsertIfPreviousDidNotEnd(false);
    }

    @Test
    void shouldNotUpdateOnInsertIfPreviousDidNotEndWhenUsingDbTime() {
        shouldNotUpdateOnInsertIfPreviousDidNotEnd(true);
    }

    private void shouldNotUpdateOnInsertIfPreviousDidNotEnd(boolean usingDbTime) {
        JdbcTemplateStorageAccessor accessor = getAccessor(usingDbTime);

        assertThat(
            accessor.insertRecord(lockConfig(MY_LOCK, Duration.ofSeconds(10)))
        ).isEqualTo(true);

        Timestamp originalLockValidity = testUtils.getLockedUntil(MY_LOCK);

        assertThat(
            accessor.insertRecord(lockConfig(MY_LOCK, Duration.ofSeconds(10)))
        ).isEqualTo(false);

        assertThat(testUtils.getLockedUntil(MY_LOCK)).isEqualTo(originalLockValidity);
    }

    @Test
    void shouldNotUpdateOtherLockConfigurationsWhenNotUsingDbTime() throws InterruptedException {
        shouldNotUpdateOtherLockConfigurations(false);
    }

    @Test
    void shouldNotUpdateOtherLockConfigurationsWhenUsingDbTime() throws InterruptedException {
        shouldNotUpdateOtherLockConfigurations(true);
    }

    private void shouldNotUpdateOtherLockConfigurations(boolean usingDbTime) throws InterruptedException {
        JdbcTemplateStorageAccessor accessor = getAccessor(usingDbTime);

        Duration lockAtMostFor = Duration.ofMillis(10);
        assertThat(accessor.insertRecord(lockConfig(MY_LOCK, lockAtMostFor))).isEqualTo(true);
        assertThat(accessor.insertRecord(lockConfig(OTHER_LOCK, lockAtMostFor))).isEqualTo(true);

        Timestamp myLockLockedUntil = testUtils.getLockedUntil(MY_LOCK);
        Timestamp otherLockLockedUntil = testUtils.getLockedUntil(OTHER_LOCK);

        // wait for a while so there will be a difference in the timestamp
        // when system time is used seems there is no milliseconds in the timestamp so to make a difference we have to wait for at least a second
        sleep(1000);


        // act
        assertThat(accessor.updateRecord(new LockConfiguration(now(), MY_LOCK, lockAtMostFor, Duration.ZERO))).isEqualTo(true);


        // assert
        assertThat(testUtils.getLockedUntil(MY_LOCK)).isAfter(myLockLockedUntil);
        // check that the other lock has not been affected by "my-lock" update
        assertThat(testUtils.getLockedUntil(OTHER_LOCK)).isEqualTo(otherLockLockedUntil);
    }

    @NonNull
    private LockConfiguration lockConfig(String myLock, Duration lockAtMostFor) {
        return new LockConfiguration(now(), myLock, lockAtMostFor, Duration.ZERO);
    }

    @NonNull
    protected JdbcTemplateStorageAccessor getAccessor(boolean usingDbTime) {
        JdbcTemplateLockProvider.Configuration.Builder builder = JdbcTemplateLockProvider
            .Configuration.builder()
            .withJdbcTemplate(testUtils.getJdbcTemplate());
        if (usingDbTime) {
            builder.usingDbTime();
        }

        return new JdbcTemplateStorageAccessor(builder.build());
    }

    protected JdbcTestUtils getTestUtils() {
        return testUtils;
    }
}
