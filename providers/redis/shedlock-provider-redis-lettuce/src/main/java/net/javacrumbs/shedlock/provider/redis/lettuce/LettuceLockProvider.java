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

import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.DEFAULT_KEY_PREFIX;
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.ENV_DEFAULT;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockTemplate;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Uses Redis's `SET resource-name anystring NX PX max-lock-ms-time` as locking
 * mechanism.
 *
 * <p>
 * See <a href="https://redis.io/commands/set">Set command</a>
 */
public class LettuceLockProvider implements ExtensibleLockProvider {

    private final InternalRedisLockProvider internalRedisLockProvider;

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
        this.internalRedisLockProvider = new InternalRedisLockProvider(
                new LettuceRedisLockTemplate(connection), environment, DEFAULT_KEY_PREFIX);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        return internalRedisLockProvider.lock(lockConfiguration);
    }

    private record LettuceRedisLockTemplate(StatefulRedisConnection<String, String> connection)
            implements InternalRedisLockTemplate {

        @Override
        public boolean set(String key, String value, long expirationMs) {
            return "OK"
                    .equals(connection
                            .sync()
                            .set(key, value, SetArgs.Builder.nx().px(expirationMs)));
        }

        @Override
        public Object eval(String script, String key, String... values) {
            return connection.sync().eval(script, ScriptOutputType.INTEGER, new String[] {key}, values);
        }
    }
}
