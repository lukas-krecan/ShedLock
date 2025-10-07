/**
 * ShedLock Redis Jedis provider module.
 *
 * This module provides Redis-based lock provider implementation
 * using the Jedis Redis client library.
 */
module net.javacrumbs.shedlock.provider.redis.jedis.v4x {
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.redis.support;
    requires redis.clients.jedis;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.redis.jedis4;
}
