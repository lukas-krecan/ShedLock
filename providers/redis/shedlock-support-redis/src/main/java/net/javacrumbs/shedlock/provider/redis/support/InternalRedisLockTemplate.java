package net.javacrumbs.shedlock.provider.redis.support;

import org.jspecify.annotations.Nullable;

/**
 * Abstraction of Redis operations used by ShedLock. Internal class, please don't use directly.
 */
public interface InternalRedisLockTemplate {
    boolean setIfAbsent(String key, String value, long expirationMs);

    boolean setIfPresent(String key, String value, long expirationMs);

    @Nullable
    Object eval(String script, String key, String... values);

    void delete(String key);
}
