/**
 * Copyright 2009-2020 the original author or authors.
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
package net.javacrumbs.shedlock.provider.redis.jedis;

import com.playtika.test.redis.RedisProperties;
import com.playtika.test.redis.wait.RedisClusterStatusCheck;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.function.Consumer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Testcontainers
public abstract class AbstractJedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractJedisLockProviderIntegrationTest.class);

    protected final static int PORT = 6379;

    @Container
    public static final RedisContainer redis = new RedisContainer();

    protected static JedisPool jedisPool;

    protected final static String ENV = "test";

    @BeforeEach
    public void initPool() {
        jedisPool = new JedisPool(redis.getContainerIpAddress(), PORT);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNull(jedis.get(JedisLockProvider.buildKey(lockName, ENV)));
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            assertNotNull(jedis.get(JedisLockProvider.buildKey(lockName, ENV)));
        }
    }

    static class RedisContainer extends FixedHostPortGenericContainer<RedisContainer> {
        public RedisContainer() {
            super("redis:5-alpine");

            RedisProperties properties = new RedisProperties();
            properties.host = "localhost";
            properties.port = PORT;
            properties.requirepass = false;

            Consumer<OutputFrame> consumer = frame -> LOGGER.info(frame.getUtf8String());
            this.withFixedExposedPort(PORT, PORT)
                .withExposedPorts(PORT)
                .withLogConsumer(consumer)
                .withCopyFileToContainer(MountableFile.forClasspathResource("redis.conf"), "/data/redis.conf")
                .withCopyFileToContainer(MountableFile.forClasspathResource("nodes.conf"), "/data/nodes.conf")
                .waitingFor(new RedisClusterStatusCheck(properties))
                //.waitingFor(new RedisStatusCheck(properties))
                .withCommand("redis-server", "/data/redis.conf");
        }
    }
}
