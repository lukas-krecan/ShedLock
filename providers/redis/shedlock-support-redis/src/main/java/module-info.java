/**
 * ShedLock Redis support module.
 *
 * This module provides common Redis support utilities for Redis-based
 * lock provider implementations.
 */
module net.javacrumbs.shedlock.provider.redis.support {
    requires java.base;
    requires net.javacrumbs.shedlock.core;

    // Export Redis support packages
    exports net.javacrumbs.shedlock.provider.redis.support;
}
