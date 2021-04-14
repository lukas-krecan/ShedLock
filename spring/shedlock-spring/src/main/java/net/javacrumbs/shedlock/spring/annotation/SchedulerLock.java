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
package net.javacrumbs.shedlock.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchedulerLock {
    /**
     * Lock name.
     */
    String name() default "";


    /**
     * How long the lock should be kept in case the machine which obtained the lock died before releasing it.
     * This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.
     *
     * Can be either time with suffix like 10s or ISO8601 duration as described in {@link java.time.Duration#parse(CharSequence)}, for example PT30S.
     */
    String lockAtMostFor() default "";


    /**
     * The lock will be held at least for given duration. Can be used if you really need to execute the task
     * at most once in given period of time. If the duration of the task is shorter than clock difference between nodes, the task can
     * be theoretically executed more than once (one node after another). By setting this parameter, you can make sure that the
     * lock will be kept at least for given period of time.
     *
     * Can be either time with suffix like 10s or ISO8601 duration as described in {@link java.time.Duration#parse(CharSequence)}, for example PT30S.
     */
    String lockAtLeastFor() default "";
}
