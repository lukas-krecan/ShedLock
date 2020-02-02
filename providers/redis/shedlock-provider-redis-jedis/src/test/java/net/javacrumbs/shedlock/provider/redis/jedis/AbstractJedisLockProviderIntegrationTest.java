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
package net.javacrumbs.shedlock.provider.redis.jedis;

import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.Before;
import org.junit.Rule;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public abstract class AbstractJedisLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Rule
    public final GenericContainer redis = new GenericContainer<>("redis:5-alpine")
        .withExposedPorts(PORT)
        .withCopyFileToContainer(MountableFile.forClasspathResource("redis.conf"), "/data/redis.conf")
        .withCopyFileToContainer(MountableFile.forClasspathResource("nodes.conf"), "/data/nodes.conf");


    protected static JedisPool jedisPool;

    protected final static int PORT = 6379;
    protected final static String ENV = "test";

    @Before
    public void initPool() {
        jedisPool = new JedisPool(redis.getContainerIpAddress(), redis.getFirstMappedPort());
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
}
