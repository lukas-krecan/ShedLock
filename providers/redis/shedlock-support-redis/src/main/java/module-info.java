/**
 * ShedLock Redis support module.
 *
 * This module provides common Redis support utilities for Redis-based
 * lock provider implementations.
 */
module net.javacrumbs.shedlock.provider.redis.support {
    requires net.javacrumbs.shedlock.core;

    // Export Redis support package
    exports net.javacrumbs.shedlock.provider.redis.support;
}
