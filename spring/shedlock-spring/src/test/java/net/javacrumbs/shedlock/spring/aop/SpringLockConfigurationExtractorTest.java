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

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class SpringLockConfigurationExtractorTest extends AbstractSpringLockConfigurationExtractorTest {

    @SchedulerLock(name = "lockName", lockAtMostFor = "100")
    public void annotatedMethod() {

    }

    @SchedulerLock(name = "lockName", lockAtMostFor = "${placeholder}")
    public void annotatedMethodWithString() {

    }

    @SchedulerLock(name = "lockName", lockAtMostFor = "PT1S")
    public void annotatedMethodWithDurationString() {

    }

    @SchedulerLock(name = "${name}")
    public void annotatedMethodWithNameVariable() {

    }

    @SchedulerLock(name = "lockName")
    public void annotatedMethodWithoutLockAtMostFor() {

    }

    @SchedulerLock(name = "lockName", lockAtLeastFor = "0")
    public void annotatedMethodWithZeroGracePeriod() {

    }

    @SchedulerLock(name = "lockName", lockAtLeastFor = "10")
    public void annotatedMethodWithPositiveGracePeriod() {

    }

    @SchedulerLock(name = "lockName", lockAtLeastFor = "10ms")
    public void annotatedMethodWithPositiveGracePeriodWithString() {

    }

    @SchedulerLock(name = "lockName", lockAtLeastFor = "-1s")
    public void annotatedMethodWithNegativeGracePeriod() {

    }

    @ScheduledLocked(name = "lockName1")
    public void composedAnnotation() {

    }

    public void methodWithoutAnnotation() {

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
