/**
 * ShedLock Apache Ignite provider module.
 *
 * This module provides Apache Ignite-based lock provider implementation
 * using the Apache Ignite core library.
 */
module net.javacrumbs.shedlock.provider.ignite {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires ignite.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.ignite;
}
