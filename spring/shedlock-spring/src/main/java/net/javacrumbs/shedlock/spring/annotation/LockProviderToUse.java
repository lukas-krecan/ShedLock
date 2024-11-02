package net.javacrumbs.shedlock.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows to disambiguate between lock providers. For performance and compatibility reasons, the annotation has no effect
 * if there is only one lock provider in the application context. Can be applied to method, type or package.
 */
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LockProviderToUse {
    String value();
}
