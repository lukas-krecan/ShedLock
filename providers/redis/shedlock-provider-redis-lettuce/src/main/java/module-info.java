/**
 * ShedLock Redis Lettuce provider module.
 *
 * This module provides Redis-based lock provider implementation
 * using the Lettuce Redis client library.
 */
module net.javacrumbs.shedlock.provider.redis.lettuce {
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.redis.support;
    requires lettuce.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.redis.lettuce;
}
