/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.javacrumbs.shedlock.spring.proxytest.BeanInterface;
import net.javacrumbs.shedlock.spring.proxytest.DynamicProxyConfig;
import net.javacrumbs.shedlock.spring.proxytest.SubclassProxyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringValueResolver;

public class SpringLockConfigurationExtractorTest {

    private static final Duration DEFAULT_LOCK_TIME = Duration.of(30, ChronoUnit.MINUTES);
    private static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.of(5, ChronoUnit.MILLIS);
    private final StringValueResolver embeddedValueResolver = mock(StringValueResolver.class);
    private final SpringLockConfigurationExtractor extractor = new SpringLockConfigurationExtractor(
            DEFAULT_LOCK_TIME, DEFAULT_LOCK_AT_LEAST_FOR, embeddedValueResolver, new StringToDurationConverter());

    @SchedulerLock(name = "lockName", lockAtMostFor = "100")
    public void annotatedMethod() {}

    @SchedulerLock(name = "lockName", lockAtMostFor = "${placeholder}")
    public void annotatedMethodWithString() {}

    @SchedulerLock(name = "lockName", lockAtMostFor = "PT1S")
    public void annotatedMethodWithDurationString() {}

    @SchedulerLock(name = "${name}")
    public void annotatedMethodWithNameVariable() {}

    @SchedulerLock(name = "lockName-#{#arg0 + '-' + #arg1 + '-' + (1 + 2) + '-' + 'abcde'.length()}")
    public void annotatedMethodWithNameSpringExpression(String arg0, Integer arg1) {}

    @SchedulerLock(name = "${name}-#{#arg0}")
    public void annotatedMethodWithNameSpringExpressionAndVariable(String arg0) {}

    @SchedulerLock(name = "lockName")
    public void annotatedMethodWithoutLockAtMostFor() {}

    @SchedulerLock(name = "lockName", lockAtLeastFor = "0")
    public void annotatedMethodWithZeroGracePeriod() {}

    @SchedulerLock(name = "lockName", lockAtLeastFor = "10")
    public void annotatedMethodWithPositiveGracePeriod() {}

    @SchedulerLock(name = "lockName", lockAtLeastFor = "10ms")
    public void annotatedMethodWithPositiveGracePeriodWithString() {}

    @SchedulerLock(name = "lockName", lockAtLeastFor = "-1s")
    public void annotatedMethodWithNegativeGracePeriod() {}

    @ScheduledLocked(name = "lockName1")
    public void composedAnnotation() {}

    public void methodWithoutAnnotation() {}

    @Test
    public void shouldLockForDefaultTimeIfNoAnnotation() throws NoSuchMethodException {
        SpringLockConfigurationExtractor.AnnotationData annotation =
                getAnnotation("annotatedMethodWithoutLockAtMostFor");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(DEFAULT_LOCK_TIME);
    }

    @Test
    public void shouldLockTimeFromAnnotation() throws NoSuchMethodException {
        noopResolver();
        SpringLockConfigurationExtractor.AnnotationData annotation = getAnnotation("annotatedMethod");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(100, MILLIS));
    }

    @Test
    public void shouldLockTimeFromAnnotationWithString() throws NoSuchMethodException {
        mockResolvedValue("${placeholder}", "5");
        SpringLockConfigurationExtractor.AnnotationData annotation = getAnnotation("annotatedMethodWithString");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(5, MILLIS));
    }

    @Test
    public void shouldLockTimeFromAnnotationWithDurationString() throws NoSuchMethodException {
        noopResolver();
        SpringLockConfigurationExtractor.AnnotationData annotation = getAnnotation("annotatedMethodWithDurationString");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(1, SECONDS));
    }

    @Test
    public void shouldGetZeroGracePeriodFromAnnotation() throws NoSuchMethodException {
        noopResolver();
        SpringLockConfigurationExtractor.AnnotationData annotation =
                getAnnotation("annotatedMethodWithZeroGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.ZERO);
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotation() throws NoSuchMethodException {
        noopResolver();
        SpringLockConfigurationExtractor.AnnotationData annotation =
                getAnnotation("annotatedMethodWithPositiveGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotationWithString() throws NoSuchMethodException {
        noopResolver();
        SpringLockConfigurationExtractor.AnnotationData annotation =
                getAnnotation("annotatedMethodWithPositiveGracePeriodWithString");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shoulFailOnNegativeLockAtMostFor() throws NoSuchMethodException {
        noopResolver();
        SpringLockConfigurationExtractor.AnnotationData annotation =
                getAnnotation("annotatedMethodWithNegativeGracePeriod");
        assertThatThrownBy(() -> extractor.getLockAtLeastFor(annotation)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldExtractComposedAnnotation() throws NoSuchMethodException {
        mockResolvedValue("20", "20");
        SpringLockConfigurationExtractor.AnnotationData annotation = getAnnotation("composedAnnotation");
        TemporalAmount atMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(annotation.name()).isEqualTo("lockName1");
        assertThat(atMostFor).isEqualTo(Duration.of(20, MILLIS));
    }

    @Test
    public void shouldFindAnnotationOnDynamicProxy() throws NoSuchMethodException {
        doTestFindAnnotationOnProxy(DynamicProxyConfig.class);
    }

    @Test
    public void shouldFindAnnotationOnSubclassProxy() throws NoSuchMethodException {
        doTestFindAnnotationOnProxy(SubclassProxyConfig.class);
    }

    @Test
    public void shouldNotLockUnannotatedMethod() throws NoSuchMethodException {
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "methodWithoutAnnotation");
        Optional<LockConfiguration> lockConfiguration = extractor.getLockConfiguration(runnable);
        assertThat(lockConfiguration).isEmpty();
    }

    @Test
    public void shouldGetNameAndLockTimeFromAnnotation() throws NoSuchMethodException {
        mockResolvedValue("lockName", "lockName");
        mockResolvedValue("100", "100");
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "annotatedMethod");
        LockConfiguration lockConfiguration =
                extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockName");
        assertThat(lockConfiguration.getLockAtMostUntil()).isBeforeOrEqualTo(now().plus(100, MILLIS));
        assertThat(lockConfiguration.getLockAtLeastUntil())
                .isAfter(now().plus(DEFAULT_LOCK_AT_LEAST_FOR).minus(1, SECONDS));
    }

    @Test
    public void shouldGetNameFromSpringVariable() throws NoSuchMethodException {
        mockResolvedValue("${name}", "lockNameX");
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "annotatedMethodWithNameVariable");
        LockConfiguration lockConfiguration =
                extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockNameX");
    }

    @Test
    public void shouldGetNameFromSpringExpression() throws NoSuchMethodException {
        mockResolvedValue("lockName-value-1-3-5", "lockName-value-1-3-5");
        Method method =
                this.getClass().getMethod("annotatedMethodWithNameSpringExpression", String.class, Integer.class);
        LockConfiguration lockConfiguration = extractor
                .getLockConfiguration(this, method, new Object[] {"value", 1})
                .get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockName-value-1-3-5");
    }

    @Test
    public void shouldGetNameFromSpringExpressionAndSpringVariable() throws NoSuchMethodException {
        mockResolvedValue("${name}-value", "lockName-value");
        Method method = this.getClass().getMethod("annotatedMethodWithNameSpringExpressionAndVariable", String.class);
        LockConfiguration lockConfiguration = extractor
                .getLockConfiguration(this, method, new Object[] {"value"})
                .get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockName-value");
    }

    private void mockResolvedValue(String expression, String resolved) {
        when(embeddedValueResolver.resolveStringValue(expression)).thenReturn(resolved);
    }

    private void noopResolver() {
        when(embeddedValueResolver.resolveStringValue(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void doTestFindAnnotationOnProxy(Class<?> config) throws NoSuchMethodException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(config)) {
            BeanInterface bean = context.getBean(BeanInterface.class);
            assertThat(findAnnotation(bean, "method")).isNotNull();
        }
    }

    private SpringLockConfigurationExtractor.AnnotationData getAnnotation(String method) throws NoSuchMethodException {
        return findAnnotation(this, method);
    }

    private SpringLockConfigurationExtractor.AnnotationData findAnnotation(Object bean, String method)
            throws NoSuchMethodException {
        return requireNonNull(SpringLockConfigurationExtractor.findAnnotation(
                bean, bean.getClass().getMethod(method)));
    }

    @Target(METHOD)
    @Retention(RUNTIME)
    @Documented
    @Scheduled
    @SchedulerLock
    public @interface ScheduledLocked {
        @AliasFor(annotation = Scheduled.class, attribute = "cron")
        String cron() default "";

        @AliasFor(annotation = SchedulerLock.class, attribute = "lockAtMostFor")
        String lockAtMostFor() default "20";

        @AliasFor(annotation = SchedulerLock.class, attribute = "name")
        String name();
    }
}
