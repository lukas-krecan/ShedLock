package micronaut.test;

import io.micronaut.aop.Around;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@Retention(RUNTIME)
@Type(TestInterceptor.class)
public @interface TestAnnotation {
}
