package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.core.annotation.AliasFor;
import org.springframework.scheduling.annotation.Async;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Async
@SchedulerLock
public @interface MyScheduled {
    @AliasFor(annotation = SchedulerLock.class, attribute = "name")
    String name();

    @AliasFor(annotation = SchedulerLock.class, attribute = "lockAtMostFor")
    String lockAtMostFor() default "";

    @AliasFor(annotation = SchedulerLock.class, attribute = "lockAtLeastFor")
    String lockAtLeastFor() default "";
}
