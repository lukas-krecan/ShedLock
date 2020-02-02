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

import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.Before;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;

public class JedisClusterLockProviderIntegrationTest extends AbstractJedisLockProviderIntegrationTest {

    private LockProvider lockProvider;

    @Before
    public void createLockProvider() {
        JedisCluster jedisCluster = new JedisCluster(new HostAndPort(redis.getContainerIpAddress(), redis.getFirstMappedPort()));
        lockProvider = new JedisLockProvider(jedisCluster, ENV);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }
}
