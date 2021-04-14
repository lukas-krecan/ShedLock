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
package net.javacrumbs.shedlock.micronaut;


import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Type;
import net.javacrumbs.shedlock.micronaut.internal.SchedulerLockInterceptor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@Executable
@Type(SchedulerLockInterceptor.class)
public @interface SchedulerLock {
    /**
     * Lock name.
     */
    String name();

    /**
     * How long the lock should be kept in case the machine which obtained the lock died before releasing it.
     * This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes. Can be any format
     * supported by <a href="https://docs.micronaut.io/latest/guide/config.html#_duration_conversion">Duration Conversion</a>
     * <p>
     */
    String lockAtMostFor() default "";

    /**
     * The lock will be held at least for this period of time. Can be used if you really need to execute the task
     * at most once in given period of time. If the duration of the task is shorter than clock difference between nodes, the task can
     * be theoretically executed more than once (one node after another). By setting this parameter, you can make sure that the
     * lock will be kept at least for given period of time. Can be any format
     * supported by <a href="https://docs.micronaut.io/latest/guide/config.html#_duration_conversion">Duration Conversion</a>
     */
    String lockAtLeastFor() default "";
}
