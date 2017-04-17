package net.javacrumbs.shedlock.core;

import org.junit.Test;

import java.time.Duration;
import java.time.temporal.TemporalAmount;

import static java.time.Duration.ZERO;
import static java.time.temporal.ChronoUnit.MILLIS;
import static net.javacrumbs.shedlock.core.ScheduledLockConfigurer.DEFAULT_LOCK_AT_MOST_FOR;
import static org.assertj.core.api.Assertions.assertThat;

public class ScheduledLockConfigurerTest {

    private ScheduledLockConfigurer configurer = new ScheduledLockConfigurer(DEFAULT_LOCK_AT_MOST_FOR, ZERO);

    @Test
    public void shouldLockForDefaultTimeIfNoAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethodWithoutLockAtMostFor");
        TemporalAmount lockAtMostFor = configurer.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(DEFAULT_LOCK_AT_MOST_FOR);
    }

    @Test
    public void shouldLockTimeFromAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethod");
        TemporalAmount lockAtMostFor = configurer.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shouldGetZeroGracePeriodFromAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethodWithZeroGracePeriod");
        TemporalAmount gracePeriod = configurer.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(ZERO);
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethodWithPositiveGracePeriod");
        TemporalAmount gracePeriod = configurer.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    private SchedulerLock getAnnotation(String method) throws NoSuchMethodException {
        return this.getClass().getMethod(method).getAnnotation(SchedulerLock.class);
    }

    @SchedulerLock(name = "lockName", lockAtMostFor = 10)
    public void annotatedMethod() {

    }

    @SchedulerLock(name = "lockName")
    public void annotatedMethodWithoutLockAtMostFor() {

    }

    @SchedulerLock(name = "lockName", lockAtLeastFor = 0)
    public void annotatedMethodWithZeroGracePeriod() {

    }

    @SchedulerLock(name = "lockName", lockAtLeastFor = 10)
    public void annotatedMethodWithPositiveGracePeriod() {

    }
}