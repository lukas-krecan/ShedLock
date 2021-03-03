/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.spring.aop.SpringLockConfigurationExtractor.AnnotationData;
import net.javacrumbs.shedlock.spring.proxytest.BeanInterface;
import net.javacrumbs.shedlock.spring.proxytest.DynamicProxyConfig;
import net.javacrumbs.shedlock.spring.proxytest.SubclassProxyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringValueResolver;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class AbstractSpringLockConfigurationExtractorTest {
    private static final Duration DEFAULT_LOCK_TIME = Duration.of(30, ChronoUnit.MINUTES);
    private static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.of(5, ChronoUnit.MILLIS);
    private final StringValueResolver embeddedValueResolver = mock(StringValueResolver.class);
    private final SpringLockConfigurationExtractor extractor = new SpringLockConfigurationExtractor(DEFAULT_LOCK_TIME, DEFAULT_LOCK_AT_LEAST_FOR, embeddedValueResolver, new StringToDurationConverter());


    @Test
    public void shouldLockForDefaultTimeIfNoAnnotation() throws NoSuchMethodException {
        AnnotationData annotation = getAnnotation("annotatedMethodWithoutLockAtMostFor");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(DEFAULT_LOCK_TIME);
    }

    @Test
    public void shouldLockTimeFromAnnotation() throws NoSuchMethodException {
        noopResolver();
        AnnotationData annotation = getAnnotation("annotatedMethod");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(100, MILLIS));
    }

    @Test
    public void shouldLockTimeFromAnnotationWithString() throws NoSuchMethodException {
        mockResolvedValue("${placeholder}", "5");
        AnnotationData annotation = getAnnotation("annotatedMethodWithString");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(5, MILLIS));
    }

    @Test
    public void shouldLockTimeFromAnnotationWithDurationString() throws NoSuchMethodException {
        noopResolver();
        AnnotationData annotation = getAnnotation("annotatedMethodWithDurationString");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(1, SECONDS));
    }

    @Test
    public void shouldGetZeroGracePeriodFromAnnotation() throws NoSuchMethodException {
        noopResolver();
        AnnotationData annotation = getAnnotation("annotatedMethodWithZeroGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.ZERO);
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotation() throws NoSuchMethodException {
        noopResolver();
        AnnotationData annotation = getAnnotation("annotatedMethodWithPositiveGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotationWithString() throws NoSuchMethodException {
        noopResolver();
        AnnotationData annotation = getAnnotation("annotatedMethodWithPositiveGracePeriodWithString");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shoulFailOnNegativeLockAtMostFor() throws NoSuchMethodException {
        noopResolver();
        AnnotationData annotation = getAnnotation("annotatedMethodWithNegativeGracePeriod");
        assertThatThrownBy(() -> extractor.getLockAtLeastFor(annotation)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void shouldExtractComposedAnnotation() throws NoSuchMethodException {
        mockResolvedValue("20", "20");
        AnnotationData annotation = getAnnotation("composedAnnotation");
        TemporalAmount atMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(annotation.getName()).isEqualTo("lockName1");
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
        LockConfiguration lockConfiguration = extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockName");
        assertThat(lockConfiguration.getLockAtMostUntil()).isBeforeOrEqualTo(now().plus(100, MILLIS));
        assertThat(lockConfiguration.getLockAtLeastUntil()).isAfter(now().plus(DEFAULT_LOCK_AT_LEAST_FOR).minus(1, SECONDS));
    }

    @Test
    public void shouldGetNameFromSpringVariable() throws NoSuchMethodException {
        mockResolvedValue("${name}", "lockNameX");
        ScheduledMethodRunnable runnable = new ScheduledMethodRunnable(this, "annotatedMethodWithNameVariable");
        LockConfiguration lockConfiguration = extractor.getLockConfiguration(runnable).get();
        assertThat(lockConfiguration.getName()).isEqualTo("lockNameX");
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
            assertThat(extractor.findAnnotation(bean, bean.getClass().getMethod("method"))).isNotNull();
        }
    }

    private AnnotationData getAnnotation(String method) throws NoSuchMethodException {
        return extractor.findAnnotation(this, this.getClass().getMethod(method));
    }
}
