/**
 * ShedLock Micronaut integration module.
 *
 * This module provides Micronaut Framework integration for ShedLock,
 * including annotations and AOP support for method-level locking.
 */
module net.javacrumbs.shedlock.micronaut {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires io.micronaut.inject;
    requires io.micronaut.runtime;
    requires io.micronaut.aop;

    // Export Micronaut integration packages
    exports net.javacrumbs.shedlock.micronaut;
}
