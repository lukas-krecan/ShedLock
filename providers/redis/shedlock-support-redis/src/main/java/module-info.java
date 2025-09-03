/**
 * ShedLock Redis support module.
 *
 * This module provides common Redis support utilities for Redis-based
 * lock provider implementations.
 */
module net.javacrumbs.shedlock.provider.redis.support {
    requires java.base;
    requires net.javacrumbs.shedlock.core;

    // Export Redis support packages only to other ShedLock Redis modules
    exports net.javacrumbs.shedlock.provider.redis.support to
            net.javacrumbs.shedlock.provider.redis.jedis,
            net.javacrumbs.shedlock.provider.redis.lettuce,
            net.javacrumbs.shedlock.provider.redis.spring,
            net.javacrumbs.shedlock.test.support.redis;
}
