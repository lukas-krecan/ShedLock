/**
 * ShedLock Micrometer integration module.
 *
 * <p>This module provides Micrometer metrics for ShedLock execution events.
 */
module net.javacrumbs.shedlock.micrometer {
    requires micrometer.core;
    requires transitive net.javacrumbs.shedlock.core;
    requires static org.jspecify;

    exports net.javacrumbs.shedlock.micrometer;
}
