/**
 * Copyright 2009-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redis.redisson;

import com.playtika.test.redis.RedisProperties;
import com.playtika.test.redis.wait.RedisClusterStatusCheck;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
public class RedissonLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedissonLockProviderIntegrationTest.class);

    protected final static int PORT = 6379;

    @Container
    public static final GenericContainer redis;

    static {
        RedisProperties properties = new RedisProperties();
        properties.host = "localhost";
        properties.port = PORT;
        properties.requirepass = false;

        Consumer<OutputFrame> consumer = frame -> LOGGER.info(frame.getUtf8String());
        redis = new FixedHostPortGenericContainer("redis:5-alpine")
            .withFixedExposedPort(PORT, PORT)
            .withExposedPorts(PORT)
            .withLogConsumer(consumer)
            .withCopyFileToContainer(MountableFile.forClasspathResource("redis.conf"), "/data/redis.conf")
            .withCopyFileToContainer(MountableFile.forClasspathResource("nodes.conf"), "/data/nodes.conf")
            .waitingFor(new RedisClusterStatusCheck(properties))
            //.waitingFor(new RedisStatusCheck(properties))
            .withCommand("redis-server", "/data/redis.conf");
    }

    protected static RedissonClient redissonClient;

    private RedissonLockProvider lockProvider;

    protected final static String ENV = "test";


    @BeforeEach
    public void initPool() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://"+redis.getContainerIpAddress()+":"+PORT);
        redissonClient = Redisson.create(config);
        lockProvider = new RedissonLockProvider(redissonClient, ENV);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        assertThat(isLocked(lockName)).isFalse();
    }

    private boolean isLocked(String lockName) {
        RLock lock = redissonClient.getLock(lockProvider.buildKey(lockName));
        return lock.isLocked();
    }

    @Override
    protected void assertLocked(String lockName) {
        assertThat(isLocked(lockName)).isTrue();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    /**
     * Wraps lock providers and tries to lock in a new thread. Redisson lock is reentrant which is not compatible with
     * integration tests.
     */
    class NewThreadLockProviderWrapper implements LockProvider{
        private final LockProvider wrapped;

        NewThreadLockProviderWrapper(LockProvider wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public @NotNull Optional<SimpleLock> lock(@NotNull LockConfiguration lockConfiguration) {
            CompletableFuture<Optional<SimpleLock>> result = new CompletableFuture<>();
            new Thread(() -> result.complete(wrapped.lock(lockConfiguration))).start();
            try {
                return result.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
