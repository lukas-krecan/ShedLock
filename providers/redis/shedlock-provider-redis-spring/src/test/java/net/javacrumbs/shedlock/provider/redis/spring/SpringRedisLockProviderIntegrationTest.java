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

import static net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer.PORT;

import net.javacrumbs.shedlock.provider.redis.testsupport.RedisContainer;
import org.junit.jupiter.api.Nested;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.data.connection.RedissonConnectionFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public class SpringRedisLockProviderIntegrationTest {
    @Container
    public static final RedisContainer redis = new RedisContainer(PORT);

    @Nested
    class Jedis extends AbstractSpringRedisLockProviderIntegrationTest {
        public Jedis() {
            super(createJedisConnectionFactory(), false);
        }
    }

    @Nested
    class JedisSafeUpdate extends AbstractSpringRedisLockProviderIntegrationTest {
        public JedisSafeUpdate() {
            super(createJedisConnectionFactory(), true);
        }
    }

    private static RedisConnectionFactory createJedisConnectionFactory() {
        JedisConnectionFactory jedisConnectionFactory =
                new JedisConnectionFactory(new RedisStandaloneConfiguration(redis.getHost(), PORT));
        jedisConnectionFactory.afterPropertiesSet();
        return jedisConnectionFactory;
    }

    @Nested
    class LettuceSafeUpdate extends AbstractSpringRedisLockProviderIntegrationTest {
        public LettuceSafeUpdate() {
            super(createLettuceConnectionFactory(), true);
        }
    }

    @Nested
    class Lettuce extends AbstractSpringRedisLockProviderIntegrationTest {
        public Lettuce() {
            super(createLettuceConnectionFactory(), false);
        }
    }

    @Nested
    class ReactiveLettuce extends AbstractReactiveRedisLockProviderIntegrationTest {
        public ReactiveLettuce() {
            super(createLettuceConnectionFactory());
        }
    }

    private static LettuceConnectionFactory createLettuceConnectionFactory() {
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redis.getHost(), PORT);
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }

    @Nested
    class Redisson extends AbstractSpringRedisLockProviderIntegrationTest {
        public Redisson() {
            super(createRedissonConnectionFactory(), false);
        }
    }

    private static RedisConnectionFactory createRedissonConnectionFactory() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redis.getHost() + ":" + PORT);
        RedissonClient redisson = org.redisson.Redisson.create(config);
        return new RedissonConnectionFactory(redisson);
    }
}
