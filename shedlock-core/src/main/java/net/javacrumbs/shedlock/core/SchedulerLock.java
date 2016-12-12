package net.javacrumbs.shedlock.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface SchedulerLock {
    /**
     * Lock name.
     */
    String name() default "";

    /**
     * How long the lock should be kept in case the machine which obtained the lock died before releasing it.
     */
    long lockForMillis() default 60 * 60 * 1000;

    /**
     * Use lock for this method.
     */
    boolean shouldLock() default true;
}
