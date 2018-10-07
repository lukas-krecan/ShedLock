package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.junit.Test;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringValueResolver;

import java.util.Optional;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static net.javacrumbs.shedlock.spring.internal.SpringLockConfigurationExtractorTest.DEFAULT_LOCK_AT_LEAST_FOR;
import static net.javacrumbs.shedlock.spring.internal.SpringLockConfigurationExtractorTest.DEFAULT_LOCK_TIME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ScheduledMethodSpringLockConfigurationExtractorTest {
    private final StringValueResolver embeddedValueResolver = mock(StringValueResolver.class);
    private final ScheduledMethodSpringLockConfigurationExtractor extractor = new ScheduledMethodSpringLockConfigurationExtractor(DEFAULT_LOCK_TIME, DEFAULT_LOCK_AT_LEAST_FOR, embeddedValueResolver);


    @Test
    public void shouldNotLockUnannotatedMethod() throws NoSuchMethodException {
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "methodWithoutAnnotation");
        Optional<LockConfiguration> lockConfiguration = extractor.getLockConfiguration(runnable);
        assertThat(lockConfiguration).isEmpty();
    }

    @Test
    public void shouldGetNameAndLockTimeFromAnnotation() throws NoSuchMethodException {
        when(embeddedValueResolver.resolveStringValue("lockName")).thenReturn("lockName");
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "annotatedMethod");
        LockConfiguration lockConfiguration = extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockName");
        assertThat(lockConfiguration.getLockAtMostUntil()).isBeforeOrEqualTo(now().plus(100, MILLIS));
        assertThat(lockConfiguration.getLockAtLeastUntil()).isAfter(now().plus(DEFAULT_LOCK_AT_LEAST_FOR).minus(1, SECONDS));
    }

    @Test
    public void shouldGetNameFromSpringVariable() throws NoSuchMethodException {
        when(embeddedValueResolver.resolveStringValue("${name}")).thenReturn("lockNameX");
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "annotatedMethodWithNameVariable");
        LockConfiguration lockConfiguration = extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockNameX");
    }

    public void methodWithoutAnnotation() {

    }


    @SchedulerLock(name = "lockName", lockAtMostFor = 100)
    public void annotatedMethod() {

    }

    @SchedulerLock(name = "${name}")
    public void annotatedMethodWithNameVariable() {

    }
}