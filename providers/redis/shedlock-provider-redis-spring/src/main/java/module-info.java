/**
 * ShedLock Redis Spring provider module.
 *
 * This module provides Redis-based lock provider implementation
 * using Spring Data Redis.
 */
module net.javacrumbs.shedlock.provider.redis.spring {
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.redis.support;
    requires spring.data.redis;
    requires reactor.core;
    requires spring.tx;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.redis.spring;
}
