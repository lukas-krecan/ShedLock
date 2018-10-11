/**
 * Copyright 2009-2018 the original author or authors.
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
package net.javacrumbs.shedlock.spring.internal;

import net.javacrumbs.shedlock.core.SchedulerLock;
import net.javacrumbs.shedlock.spring.proxytest.BeanInterface;
import net.javacrumbs.shedlock.spring.proxytest.DynamicProxyConfig;
import net.javacrumbs.shedlock.spring.proxytest.SubclassProxyConfig;
import org.junit.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.StringValueResolver;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAmount;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SpringLockConfigurationExtractorTest {
    private static final Duration DEFAULT_LOCK_TIME = Duration.of(30, ChronoUnit.MINUTES);
    private static final Duration DEFAULT_LOCK_AT_LEAST_FOR = Duration.of(5, ChronoUnit.MILLIS);
    private final StringValueResolver embeddedValueResolver = mock(StringValueResolver.class);
    private final SpringLockConfigurationExtractor extractor = new SpringLockConfigurationExtractor(DEFAULT_LOCK_TIME, DEFAULT_LOCK_AT_LEAST_FOR, embeddedValueResolver);


    @Test
    public void shouldLockForDefaultTimeIfNoAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethodWithoutLockAtMostFor");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(DEFAULT_LOCK_TIME);
    }

    @Test
    public void shouldLockTimeFromAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethod");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(100, MILLIS));
    }

    @Test
    public void shouldLockTimeFromAnnotationWithString() throws NoSuchMethodException {
        when(embeddedValueResolver.resolveStringValue("${placeholder}")).thenReturn("5");
        SchedulerLock annotation = getAnnotation("annotatedMethodWithString");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(5, MILLIS));
    }

    @Test
    public void shouldLockTimeFromAnnotationWithDurationString() throws NoSuchMethodException {
        when(embeddedValueResolver.resolveStringValue("PT1S")).thenReturn("PT1S");
        SchedulerLock annotation = getAnnotation("annotatedMethodWithDurationString");
        TemporalAmount lockAtMostFor = extractor.getLockAtMostFor(annotation);
        assertThat(lockAtMostFor).isEqualTo(Duration.of(1, SECONDS));
    }

    @Test
    public void shouldGetZeroGracePeriodFromAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethodWithZeroGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.ZERO);
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("annotatedMethodWithPositiveGracePeriod");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shouldGetPositiveGracePeriodFromAnnotationWithString() throws NoSuchMethodException {
        when(embeddedValueResolver.resolveStringValue("10")).thenReturn("10");
        SchedulerLock annotation = getAnnotation("annotatedMethodWithPositiveGracePeriodWithString");
        TemporalAmount gracePeriod = extractor.getLockAtLeastFor(annotation);
        assertThat(gracePeriod).isEqualTo(Duration.of(10, MILLIS));
    }

    @Test
    public void shouldExtractComposedAnnotation() throws NoSuchMethodException {
        SchedulerLock annotation = getAnnotation("composedAnnotation");
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

    private void doTestFindAnnotationOnProxy(Class<?> config) throws NoSuchMethodException {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(config)) {
            BeanInterface bean = context.getBean(BeanInterface.class);
            assertThat(extractor.findAnnotation(bean, bean.getClass().getMethod("method"))).isNotNull();
        }
    }

    private SchedulerLock getAnnotation(String method) throws NoSuchMethodException {
        return extractor.findAnnotation(this, this.getClass().getMethod(method));
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

    @ScheduledLocked(name = "lockName1")
    public void composedAnnotation() {

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
        long lockAtMostFor() default 20L;

        @AliasFor(annotation = SchedulerLock.class, attribute = "name")
        String name();
    }
}