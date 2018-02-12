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
package net.javacrumbs.shedlock.provider.jedis;

import redis.clients.jedis.Jedis;
import redis.clients.util.Pool;

/**
 * @deprecated Please use net.javacrumbs.shedlock.provider.redis.jedis.JedisLockProvider in artifact shedlock-provider-redis-jedis
 */

@Deprecated
public class JedisLockProvider extends net.javacrumbs.shedlock.provider.redis.jedis.JedisLockProvider {
    private static final String KEY_PREFIX = "job-lock";

    public JedisLockProvider(Pool<Jedis> jedisPool) {
        super(jedisPool);
    }

    public JedisLockProvider(Pool<Jedis> jedisPool, String environment) {
        super(jedisPool, environment);
    }

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }
}
