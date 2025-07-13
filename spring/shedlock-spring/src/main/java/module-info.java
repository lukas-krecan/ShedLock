/**
 * ShedLock Spring integration module.
 *
 * This module provides Spring Framework integration for ShedLock,
 * including annotations and AOP support for method-level locking.
 */
module net.javacrumbs.shedlock.spring {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires spring.context;
    requires spring.core;
    requires spring.beans;
    requires spring.aop;
    requires spring.expression;
    requires org.slf4j;
    // AspectJ is optional and doesn't have proper module support

    // Export Spring integration packages
    exports net.javacrumbs.shedlock.spring;
    exports net.javacrumbs.shedlock.spring.annotation;
    exports net.javacrumbs.shedlock.spring.aop;
}
