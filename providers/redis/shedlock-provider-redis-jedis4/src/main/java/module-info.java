/**
 * ShedLock Redis Jedis provider module.
 *
 * This module provides Redis-based lock provider implementation
 * using the Jedis Redis client library.
 */
module net.javacrumbs.shedlock.provider.redis.jedis {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.redis.support;
    requires jedis;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.redis.jedis;
}
