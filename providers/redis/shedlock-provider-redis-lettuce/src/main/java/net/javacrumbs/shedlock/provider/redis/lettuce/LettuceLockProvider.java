/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redis.lettuce;

import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism.
 *
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class LettuceLockProvider implements ExtensibleLockProvider {

    private static final String KEY_PREFIX = "job-lock";
    private static final String ENV_DEFAULT = "default";

    private final StatefulRedisConnection<String, String> connection;
    private final String environment;

    public LettuceLockProvider(@NonNull StatefulRedisConnection<String, String> connection) {
        this(connection, ENV_DEFAULT);
    }

    /**
     * Creates LettuceLockProvider
     *
     * @param connection  StatefulRedisConnection
     * @param environment environment is part of the key and thus makes sure there is not
     *                    key conflict between multiple ShedLock instances running on the
     *                    same Redis
     */
    public LettuceLockProvider(
            @NonNull StatefulRedisConnection<String, String> connection, @NonNull String environment) {
        this.connection = connection;
        this.environment = environment;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        long expireTime = getMsUntil(lockConfiguration.getLockAtMostUntil());

        String key = buildKey(lockConfiguration.getName(), environment);

        String rez =
                connection.sync().set(key, buildValue(), SetArgs.Builder.nx().px(expireTime));

        if ("OK".equals(rez)) {
            return Optional.of(new RedisLock(key, connection, lockConfiguration));
        }

        return Optional.empty();
    }

    private static final class RedisLock extends AbstractSimpleLock {

        private final String key;
        private final StatefulRedisConnection<String, String> connection;

        private RedisLock(
                String key, StatefulRedisConnection<String, String> connection, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.connection = connection;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    connection.sync().del(key);
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                connection.sync().set(key, buildValue(), SetArgs.Builder.xx().px(keepLockFor));
            }
        }

        @Override
        @NonNull
        protected Optional<SimpleLock> doExtend(@NonNull LockConfiguration newConfiguration) {
            long expiryMs = getMsUntil(newConfiguration.getLockAtMostUntil());

            String result = connection
                    .sync()
                    .set(key, buildValue(), SetArgs.Builder.xx().px(expiryMs));

            if ("OK".equals(result)) {
                return Optional.of(new RedisLock(key, connection, newConfiguration));
            }

            return Optional.empty();
        }
    }

    static String buildKey(String lockName, String environment) {
        return String.format("%s:%s:%s", KEY_PREFIX, environment, lockName);
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }

    private static String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }
}
