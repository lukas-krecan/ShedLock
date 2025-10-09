/**
 * ShedLock Redis Jedis provider module.
 *
 * This module provides Redis-based lock provider implementation
 * using the Jedis Redis client library.
 */
@SuppressWarnings("module")
module net.javacrumbs.shedlock.provider.redis.jedis4 {
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.redis.support;
    requires redis.clients.jedis;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.redis.jedis4;
}
