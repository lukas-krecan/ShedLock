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

import net.javacrumbs.shedlock.spring.aop.SchedulerLockConfigurationSelector;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(SchedulerLockConfigurationSelector.class)
public @interface EnableSchedulerLock {
    enum InterceptMode {
        /**
         * Default mode when a TaskScheduler is wrapped in a proxy to ensure locking. Only applies lock
         * if the {@link net.javacrumbs.shedlock.core.SchedulerLock} annotated method is called using scheduler.
         */
        PROXY_SCHEDULER,

        /**
         * Scheduled method is proxied to ensure locking. Lock is created every time
         * {@link net.javacrumbs.shedlock.core.SchedulerLock} annotated is called (even if it is NOT called using Spring scheduler)
         */
        PROXY_METHOD
    }


    /**
     * Mode of integration, either TaskScheduler is wrapped in a proxy or the scheduled method is proxied to ensure locking
     *
     * @see <a href="https://github.com/lukas-krecan/ShedLock#modes-of-spring-integration">Modes of Spring integration</a>
     */
    InterceptMode interceptMode() default InterceptMode.PROXY_METHOD;


    /**
     * Default value how long the lock should be kept in case the machine which obtained the lock died before releasing it.
     * Can be either time with suffix like 10s or ISO8601 duration as described in {@link java.time.Duration#parse(CharSequence)}, for example PT30S.
     * This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.
     * Set this to some value much higher than normal task duration. Can be overridden in each ScheduledLock annotation.

     */
    String defaultLockAtMostFor();


    /**
     * The lock will be held at least for this duration.
     * Can be either time with suffix like 10s or ISO8601 duration as described in {@link java.time.Duration#parse(CharSequence)}, for example PT30S. Can be used if you really need to execute the task
     * at most once in given period of time. If the duration of the task is shorter than clock difference between nodes, the task can
     * be theoretically executed more than once (one node after another). By setting this parameter, you can make sure that the
     * lock will be kept at least for given period of time. Can be overridden in each ScheduledLock annotation.
     */
    String defaultLockAtLeastFor() default "PT0S";


    /**
     * Since 3.0.0 use {@link #interceptMode()} to configure the intercept mode. Had to be renamed to make it compatible
     * with Spring AOP infrastructure. Sorry.
     *
     * Indicate how advice should be applied.
     */
    AdviceMode mode() default AdviceMode.PROXY;

    /**
     * Indicate whether subclass-based (CGLIB) proxies are to be created as opposed
     * to standard Java interface-based proxies.
     */
    boolean proxyTargetClass() default false;

    /**
     * Indicate the ordering of the execution of the locking advisor
     * when multiple advices are applied at a specific joinpoint.
     * <p>The default is {@link Ordered#LOWEST_PRECEDENCE}.
     */
    int order() default Ordered.LOWEST_PRECEDENCE;
}
