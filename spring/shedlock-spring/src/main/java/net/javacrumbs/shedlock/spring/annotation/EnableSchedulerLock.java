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
     * Default duration to lock at most for as specified in {@link java.time.Duration#parse(CharSequence)}
     */
    String defaultLockAtMostFor();


    /**
     * Default duration to lock at leat for as specified in {@link java.time.Duration#parse(CharSequence)}
     */

    String defaultLockAtLeastFor() default "PT0S";
}
