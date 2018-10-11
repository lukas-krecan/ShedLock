package net.javacrumbs.shedlock.spring.annotation;

import org.springframework.context.annotation.Import;

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
         * Default mode when custom TaskScheduler is used to ensure locks are placed
         */
        WRAP_SCHEDULER,

        /**
         * Locks are created thanks to AOP proxy
         */
        PROXY_METHOD
    }


    InterceptMode mode() default InterceptMode.WRAP_SCHEDULER;


    /**
     * Default value how long the lock should be kept in case the machine which obtained the lock died before releasing it.
     * Format is described in {@link java.time.Duration#parse(CharSequence)}, for example PT30S.
     * This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.
     * Set this to some value much higher than normal task duration. Can be overridden in each ScheduledLock annotation.
     *
     * Ignored when using ZooKeeper and other lock providers which are able to detect dead node.
     */
    String defaultLockAtMostFor();


    /**
     * The lock will be held at least for this duration.
     * Format is described in {@link java.time.Duration#parse(CharSequence)}, for example PT30S. Can be used if you really need to execute the task
     * at most once in given period of time. If the duration of the task is shorter than clock difference between nodes, the task can
     * be theoretically executed more than once (one node after another). By setting this parameter, you can make sure that the
     * lock will be kept at least for given period of time. Can be overridden in each ScheduledLock annotation.
     */
    String defaultLockAtLeastFor() default "PT0S";
}
