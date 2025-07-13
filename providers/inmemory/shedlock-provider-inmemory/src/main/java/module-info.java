/**
 * ShedLock In-Memory provider module.
 *
 * This module provides in-memory lock provider implementation
 * for testing and single-instance applications.
 */
module net.javacrumbs.shedlock.provider.inmemory {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires org.slf4j;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.inmemory;
}
