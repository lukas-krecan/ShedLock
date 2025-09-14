/**
 * ShedLock core module providing the main locking API and interfaces.
 *
 * This module contains the core abstractions for distributed locking:
 * - LockProvider interface for implementing lock providers
 * - LockConfiguration for configuring locks
 * - LockingTaskExecutor for executing tasks with locks
 * - Support utilities for building lock providers
 */
module net.javacrumbs.shedlock.core {
    requires java.base;
    requires transitive org.slf4j;
    requires transitive static org.jspecify;

    // Export main API packages
    exports net.javacrumbs.shedlock.core;
    exports net.javacrumbs.shedlock.support;
    exports net.javacrumbs.shedlock.util;
}
