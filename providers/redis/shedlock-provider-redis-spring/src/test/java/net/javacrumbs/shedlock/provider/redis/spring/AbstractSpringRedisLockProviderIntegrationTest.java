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
package net.javacrumbs.shedlock.provider.redis.spring;

import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.provider.redis.testsupport.AbstractRedisIntegrationTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

public abstract class AbstractSpringRedisLockProviderIntegrationTest extends AbstractRedisIntegrationTest {

    private final RedisLockProvider lockProvider;
    private final StringRedisTemplate redisTemplate;

    private static final String ENV = "test";
    private static final String KEY_PREFIX = "test-prefix";

    public AbstractSpringRedisLockProviderIntegrationTest(
            RedisConnectionFactory connectionFactory, boolean safeUpdate) {
        lockProvider = new RedisLockProvider.Builder(connectionFactory)
                .environment(ENV)
                .keyPrefix(KEY_PREFIX)
                .safeUpdate(safeUpdate)
                .build();

        redisTemplate = new StringRedisTemplate(connectionFactory);
    }

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected String getLock(String lockName) {
        return redisTemplate.opsForValue().get(buildKey(lockName));
    }

    private String buildKey(String lockName) {
        return buildKey(lockName, KEY_PREFIX, ENV);
    }
}
