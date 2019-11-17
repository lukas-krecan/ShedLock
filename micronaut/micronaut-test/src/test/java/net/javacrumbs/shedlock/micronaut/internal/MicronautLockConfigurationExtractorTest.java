package net.javacrumbs.shedlock.micronaut.internal;

import io.micronaut.context.AbstractExecutableMethod;
import io.micronaut.core.type.Argument;
import net.javacrumbs.shedlock.micronaut.annotation.SchedulerLock;
import net.javacrumbs.shedlock.micronaut.internal.MicronautLockConfigurationExtractor.AnnotationData;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

import static java.time.temporal.ChronoUnit.MILLIS;
import static org.assertj.core.api.Java6Assertions.assertThat;


class MicronautLockConfigurationExtractorTest {
    private static final Duration DEFAULT_LOCK_TIME = Duration.of(30, ChronoUnit.MINUTES);
    private static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.of(5, ChronoUnit.MILLIS);
    private final MicronautLockConfigurationExtractor extractor = new MicronautLockConfigurationExtractor(DEFAULT_LOCK_TIME, DEFAULT_LOCK_AT_LEAST_FOR);


    @Test
    public void shouldLockForDefaultTimeIfNoAnnotation() throws NoSuchMethodException {
        AnnotationData annotation = getAnnotation("annotatedMethodWithoutLockAtMostFor");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(DEFAULT_LOCK_TIME);
    }

    @Test
    public void shouldLockTimeFromAnnotation() throws NoSuchMethodException {
        AnnotationData annotation = getAnnotation("annotatedMethod");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(100, MILLIS));
    }

//    @Test
//    public void shouldLockTimeFromAnnotationWithString() throws NoSuchMethodException {
//        when(embeddedValueResolver.resolveStringValue("${placeholder}")).thenReturn("5");
//        AnnotationData annotation = getAnnotation("annotatedMethodWithString");
//        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
//        assertThat(lockAtMostFor).isEqualTo(Duration.of(5, MILLIS));
//    }
//
//    @Test
//    public void shouldLockTimeFromAnnotationWithDurationString() throws NoSuchMethodException {
//        when(embeddedValueResolver.resolveStringValue("PT1S")).thenReturn("PT1S");
//        AnnotationData annotation = getAnnotation("annotatedMethodWithDurationString");
//        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
//        assertThat(lockAtMostFor).isEqualTo(Duration.of(1, SECONDS));
//    }

    @Test
    public void shouldGetZeroGracePeriodFromAnnotation() throws NoSuchMethodException {
        AnnotationData annotation = getAnnotation("annotatedMethodWithZeroGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.ZERO);
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotation() throws NoSuchMethodException {
        AnnotationData annotation = getAnnotation("annotatedMethodWithPositiveGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

//    @Test
//    public void shouldGetPositiveGracePeriodFromAnnotationWithString() throws NoSuchMethodException {
//        when(embeddedValueResolver.resolveStringValue("10")).thenReturn("10");
//        AnnotationData annotation = getAnnotation("annotatedMethodWithPositiveGracePeriodWithString");
//        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
//        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
//    }

    @Test
    public void shouldExtractComposedAnnotation() throws NoSuchMethodException {
        AnnotationData annotation = getAnnotation("composedAnnotation");
        TemporalAmount atMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(annotation.getName()).isEqualTo("lockName1");
        assertThat(atMostFor).isEqualTo(Duration.of(20, MILLIS));
    }


    private AnnotationData getAnnotation(String methodName) throws NoSuchMethodException {
        AbstractExecutableMethod method = new AbstractExecutableMethod(this.getClass(), methodName, Argument.VOID) {
            @Override
            protected Object invokeInternal(Object instance, Object[] arguments) {
                return null;
            }
        };
        return extractor.findAnnotation(method);
    }

    @SchedulerLock(name = "lockName", lockAtMostFor = 100)
    public void annotatedMethod() {

    }

    @SchedulerLock(name = "lockName", lockAtMostForString = "${placeholder}")
    public void annotatedMethodWithString() {

    }

    @SchedulerLock(name = "lockName", lockAtMostForString = "PT1S")
    public void annotatedMethodWithDurationString() {

    }

    @SchedulerLock(name = "${name}")
    public void annotatedMethodWithNameVariable() {

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

    @SchedulerLock(name = "lockName", lockAtLeastForString = "10")
    public void annotatedMethodWithPositiveGracePeriodWithString() {

    }

    public void methodWithoutAnnotation() {

    }
}