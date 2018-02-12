/**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redis.jedis;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.embedded.RedisServer;

import java.io.IOException;

public class JedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static JedisPool jedisPool;
    private static RedisServer redisServer;
    private LockProvider lockProvider;

    private final static int PORT = 6380;
    private final static String HOST = "localhost";
    private final static String ENV = "test";

    @BeforeClass
    public static void startRedis() throws IOException {
        redisServer = new RedisServer(PORT);
        redisServer.start();
    }

    @AfterClass
    public static void stopRedis() {
        redisServer.stop();
    }

    @Before
    public void createLockProvider() {
        jedisPool = new JedisPool(HOST, PORT);
        lockProvider = new JedisLockProvider(jedisPool, ENV);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            Assert.assertNull(jedis.get(JedisLockProvider.buildKey(lockName, ENV)));
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        try (Jedis jedis = jedisPool.getResource()) {
            Assert.assertNotNull(jedis.get(JedisLockProvider.buildKey(lockName, ENV)));
        }
    }
}