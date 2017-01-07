package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SchedulerLock;
import org.junit.Test;
import org.springframework.scheduling.support.ScheduledMethodRunnable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringLockConfigurationExtractorTest {
    private final SpringLockConfigurationExtractor extractor = new SpringLockConfigurationExtractor();


    @Test
    public void shouldExtractMethodName() throws NoSuchMethodException {
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "methodWithoutAnnotation");
        Optional<LockConfiguration> lockConfiguration = extractor.getLockConfiguration(runnable);
        assertThat(lockConfiguration).isEmpty();
    }

    @Test
    public void shouldGetNameAndLockTimeFromAnnotation() throws NoSuchMethodException {
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "annotatedMethod");
        LockConfiguration lockConfiguration = extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockName");
        assertThat(lockConfiguration.getLockUntil()).isLessThan(Instant.now().plus(11, ChronoUnit.MILLIS));
    }

    public void methodWithoutAnnotation() {

    }

    @SchedulerLock(name = "lockName", lockAtMostFor = 10)
    public void annotatedMethod() {

    }
}