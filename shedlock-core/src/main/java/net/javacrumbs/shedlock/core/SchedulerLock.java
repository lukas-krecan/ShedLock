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
    String name();

    /**
     * How long (in ms) the lock should be kept in case the machine which obtained the lock died before releasing it.
     * This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.
     *
     * Ignored when using ZooKeeper and other lock providers which are able to detect dead node.
     */
    long lockAtMostFor() default 60 * 60 * 1000;
}
