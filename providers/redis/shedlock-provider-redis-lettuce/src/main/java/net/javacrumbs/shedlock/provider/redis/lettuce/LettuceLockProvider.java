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

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

    /*
     * https://redis.io/docs/latest/develop/use/patterns/distributed-locks/
     * */
    private static final String delLuaScript =
            """
        if redis.call("get",KEYS[1]) == ARGV[1] then
            return redis.call("del",KEYS[1])
        else
            return 0
        end
        """;

    private static final String updLuaScript =
            """
        if redis.call('get', KEYS[1]) == ARGV[1] then
           return redis.call('pexpire', KEYS[1], ARGV[2])
        else
           return 0
        end
        """;

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
        String uniqueValue = buildValue();

        String rez =
                connection.sync().set(key, uniqueValue, SetArgs.Builder.nx().px(expireTime));

        if ("OK".equals(rez)) {
            return Optional.of(new RedisLock(key, uniqueValue, connection, lockConfiguration));
        }

        return Optional.empty();
    }

    private static final class RedisLock extends AbstractSimpleLock {

        private final String key;
        private final String value;
        private final StatefulRedisConnection<String, String> connection;

        private RedisLock(
                String key,
                String value,
                StatefulRedisConnection<String, String> connection,
                LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.value = value;
            this.connection = connection;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getMsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    connection
                            .sync()
                            .eval(delLuaScript, ScriptOutputType.INTEGER, new String[] {key}, new String[] {value});
                } catch (Exception e) {
                    throw new LockException("Can not remove node", e);
                }
            } else {
                LettuceLockProvider.extend(this, keepLockFor);
            }
        }

        @Override
        @NonNull
        protected Optional<SimpleLock> doExtend(@NonNull LockConfiguration newConfiguration) {
            long expiryMs = getMsUntil(newConfiguration.getLockAtMostUntil());

            if (LettuceLockProvider.extend(this, expiryMs)) {
                return Optional.of(new RedisLock(key, value, connection, newConfiguration));
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
        return String.format("ADDED:%s@%s:%s", toIsoString(ClockProvider.now()), getHostname(), UUID.randomUUID());
    }

    private static boolean extend(RedisLock lock, long expiryMs) {
        return lock.connection
                .sync()
                .eval(
                        updLuaScript,
                        ScriptOutputType.INTEGER,
                        new String[] {lock.key},
                        lock.value,
                        String.valueOf(expiryMs))
                .equals(1L);
    }
}
