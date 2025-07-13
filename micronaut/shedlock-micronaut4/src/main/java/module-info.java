/**
 * ShedLock Micronaut 4 integration module.
 *
 * This module provides Micronaut 4 framework integration
 * for ShedLock distributed locking.
 */
module net.javacrumbs.shedlock.micronaut4 {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires jakarta.inject;
    requires io.micronaut.micronaut_aop;
    requires io.micronaut.micronaut_core;
    requires io.micronaut.micronaut_inject;

    // Export public packages
    exports net.javacrumbs.shedlock.micronaut;
}
