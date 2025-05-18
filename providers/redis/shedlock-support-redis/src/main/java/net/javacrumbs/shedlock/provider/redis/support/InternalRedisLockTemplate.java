package net.javacrumbs.shedlock.provider.redis.support;

/**
 * Abstraction of Redis operations used by ShedLock. Internal class, please don't use directly.
 */
public interface InternalRedisLockTemplate {
    boolean set(String key, String value, long expirationMs);

    Object eval(String script, String key, String... values);
}
