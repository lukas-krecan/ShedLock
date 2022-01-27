package net.javacrumbs.shedlock.provider.memcached.spy;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import net.spy.memcached.MemcachedClient;
import net.spy.memcached.ops.OperationStatus;
import net.spy.memcached.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Lock Provider for Memcached
 *
 * @see <a href="https://memcached.org/">memcached</a>
 */
public class MemcachedLockProvider implements LockProvider {

    /**
     * KEY PREFIX
     */
    private static final String KEY_PREFIX = "shedlock";

    /**
     * ENV DEFAULT
     */
    private static final String ENV_DEFAULT = "default";

    private final MemcachedClient client;

    private final String env;

    /**
     * Create MemcachedLockProvider
     * @param client Spy.memcached.MemcachedClient
     */
    public MemcachedLockProvider(@NonNull MemcachedClient client){
        this(client, ENV_DEFAULT);
    }

    /**
     * Create MemcachedLockProvider
     * @param client Spy.memcached.MemcachedClient
     * @param env is part of the key and thus makes sure there is not key conflict between multiple ShedLock instances
     *            running on the same memcached
     */
    public MemcachedLockProvider(@NonNull MemcachedClient client, @NonNull String env){
        this.client = client;
        this.env = env;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration){
        long expireTime = getSecondUntil(lockConfiguration.getLockAtMostUntil());
        String key = buildKey(lockConfiguration.getName(), this.env);
        OperationStatus status = client.add(key, (int) expireTime, buildValue()).getStatus();
        if (status.isSuccess()) {
            return Optional.of(new MemcachedLock(key, client, lockConfiguration));
        }
        return Optional.empty();
    }


    private static long getSecondUntil(Instant instant) {
        long millis = Duration.between(ClockProvider.now(), instant).toMillis();
        return  millis / 1000;
    }

    static String buildKey(String lockName, String env) {
        String k = String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
        StringUtils.validateKey(k, false);
        return k;
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }


    private static final class MemcachedLock extends AbstractSimpleLock {

        private final String key;

        private final MemcachedClient client;

        private MemcachedLock(@NonNull String key,
                              @NonNull MemcachedClient client,
                              @NonNull LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.client = client;
        }

        @Override
        protected void doUnlock() {
            long keepLockFor = getSecondUntil(lockConfiguration.getLockAtLeastUntil());
            if (keepLockFor <= 0) {
                OperationStatus status = client.delete(key).getStatus();
                if (!status.isSuccess()) {
                    throw new LockException("Can not remove node. " + status.getMessage());
                }
            } else {
                OperationStatus status = client.replace(key, (int) keepLockFor, buildValue()).getStatus();
                if (!status.isSuccess()) {
                    throw new LockException("Can not replace node. " + status.getMessage());
                }
            }
        }
    }

}
