package net.javacrumbs.shedlock.spring.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LockProviderBeanName {
    String value();
}
