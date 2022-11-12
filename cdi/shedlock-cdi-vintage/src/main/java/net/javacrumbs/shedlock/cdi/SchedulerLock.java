package net.javacrumbs.shedlock.cdi;

import javax.enterprise.util.Nonbinding;
import javax.interceptor.InterceptorBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@InterceptorBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Inherited
public @interface SchedulerLock {
    /**
     * Lock name.
     */
    @Nonbinding String name();

    /**
     * How long the lock should be kept in case the machine which obtained the lock died before releasing it.
     * This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes. Can be any format
     * supported by <a href="https://docs.micronaut.io/latest/guide/config.html#_duration_conversion">Duration Conversion</a>
     * <p>
     */
    @Nonbinding String lockAtMostFor() default "";

    /**
     * The lock will be held at least for this period of time. Can be used if you really need to execute the task
     * at most once in given period of time. If the duration of the task is shorter than clock difference between nodes, the task can
     * be theoretically executed more than once (one node after another). By setting this parameter, you can make sure that the
     * lock will be kept at least for given period of time. Can be any format
     * supported by <a href="https://docs.micronaut.io/latest/guide/config.html#_duration_conversion">Duration Conversion</a>
     */
    @Nonbinding String lockAtLeastFor() default "";
}

