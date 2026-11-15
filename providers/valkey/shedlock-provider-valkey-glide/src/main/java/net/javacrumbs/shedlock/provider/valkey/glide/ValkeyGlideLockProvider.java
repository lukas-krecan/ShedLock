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
package net.javacrumbs.shedlock.provider.valkey.glide;

import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.DEFAULT_KEY_PREFIX;
import static net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider.ENV_DEFAULT;

import glide.api.GlideClient;
import glide.api.models.Script;
import glide.api.models.commands.ScriptOptions;
import glide.api.models.commands.SetOptions;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockProvider;
import net.javacrumbs.shedlock.provider.redis.support.InternalRedisLockTemplate;
import net.javacrumbs.shedlock.support.LockException;
import org.jspecify.annotations.Nullable;

/**
 * Uses Valkey/Redis {@code SET key value NX PX milliseconds} via
 * <a href="https://github.com/valkey-io/valkey-glide">Valkey GLIDE</a> as locking mechanism.
 *
 * <p>See <a href="https://valkey.io/commands/set/">SET command</a>
 */
public class ValkeyGlideLockProvider implements ExtensibleLockProvider {

    private final InternalRedisLockProvider internalRedisLockProvider;

    public ValkeyGlideLockProvider(GlideClient client) {
        this(client, ENV_DEFAULT);
    }

    /**
     * Creates ValkeyGlideLockProvider
     *
     * @param client GlideClient
     * @param environment environment is part of the key and thus makes sure there is not key
     *     conflict between multiple ShedLock instances running on the same Valkey/Redis
     */
    public ValkeyGlideLockProvider(GlideClient client, String environment) {
        this(client, environment, false);
    }

    /**
     * Creates ValkeyGlideLockProvider
     *
     * @param client GlideClient
     * @param environment environment is part of the key and thus makes sure there is not key
     *     conflict between multiple ShedLock instances running on the same Valkey/Redis
     * @param safeUpdate When set to true and the lock is held for more than lockAtMostFor, and the
     *     lock is already held by somebody else, we don't release/extend the lock.
     */
    public ValkeyGlideLockProvider(GlideClient client, String environment, boolean safeUpdate) {
        this.internalRedisLockProvider = new InternalRedisLockProvider(
                new GlideRedisLockTemplate(client), environment, DEFAULT_KEY_PREFIX, safeUpdate);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        return internalRedisLockProvider.lock(lockConfiguration);
    }

    private record GlideRedisLockTemplate(GlideClient client) implements InternalRedisLockTemplate {

        @Override
        public boolean setIfAbsent(String key, String value, long expirationMs) {
            return set(
                    key,
                    value,
                    SetOptions.builder()
                            .conditionalSet(SetOptions.ConditionalSet.ONLY_IF_DOES_NOT_EXIST)
                            .expiry(SetOptions.Expiry.Milliseconds(expirationMs))
                            .build());
        }

        @Override
        public boolean setIfPresent(String key, String value, long expirationMs) {
            return set(
                    key,
                    value,
                    SetOptions.builder()
                            .conditionalSet(SetOptions.ConditionalSet.ONLY_IF_EXISTS)
                            .expiry(SetOptions.Expiry.Milliseconds(expirationMs))
                            .build());
        }

        private boolean set(String key, String value, SetOptions options) {
            return "OK".equals(await(client.set(key, value, options)));
        }

        @Override
        public @Nullable Object eval(String script, String key, String... values) {
            ScriptOptions.ScriptOptionsBuilder<?, ?> options =
                    ScriptOptions.builder().key(key);
            for (String value : values) {
                options.arg(value);
            }
            try (Script luaScript = new Script(script, Boolean.FALSE)) {
                return await(client.invokeScript(luaScript, options.build()));
            } catch (Exception e) {
                throw new LockException("Can not evaluate Lua script", e);
            }
        }

        @Override
        public void delete(String key) {
            await(client.del(new String[] {key}));
        }

        private static <T> @Nullable T await(java.util.concurrent.CompletableFuture<T> future) {
            try {
                return future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LockException("Interrupted while waiting for Valkey command", e);
            } catch (ExecutionException e) {
                throw new LockException("Valkey command failed", e.getCause() != null ? e.getCause() : e);
            }
        }
    }
}
