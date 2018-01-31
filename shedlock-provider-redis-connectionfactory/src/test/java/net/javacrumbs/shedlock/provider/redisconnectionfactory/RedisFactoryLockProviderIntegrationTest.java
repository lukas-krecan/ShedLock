/**
 * Copyright 2009-2017 the original author or authors.
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
package net.javacrumbs.shedlock.provider.redisconnectionfactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import redis.embedded.RedisServer;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

public class RedisFactoryLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

	private static JedisConnectionFactory jedisConnectionFactory;
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
		jedisConnectionFactory = new JedisConnectionFactory();
		jedisConnectionFactory.setHostName(HOST);
		jedisConnectionFactory.setPort(PORT);
		lockProvider = new RedisFactoryLockProvider(jedisConnectionFactory, ENV);
	}

	@Override
	protected LockProvider getLockProvider() {
		return lockProvider;
	}

	@Override
	protected void assertUnlocked(String lockName) {
		RedisConnection redisConnection = null;
		try {
			redisConnection = jedisConnectionFactory.getConnection();
			Assert.assertNull(redisConnection.get(RedisFactoryLockProvider.buildKey(lockName, ENV).getBytes()));
		} finally {
			if (redisConnection != null)
				redisConnection.close();
		}
	}

	@Override
	protected void assertLocked(String lockName) {
		RedisConnection redisConnection = null;
		try {
			redisConnection = jedisConnectionFactory.getConnection();
			Assert.assertNotNull(redisConnection.get(RedisFactoryLockProvider.buildKey(lockName, ENV).getBytes()));
		} finally {
			if (redisConnection != null)
				redisConnection.close();
		}
	}

	@Override
	public void shouldTimeout() throws InterruptedException {
		LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, Duration.ofMillis(2), Duration.ZERO);
		Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
		assertThat(lock1).isNotEmpty();

		sleep(5);

		// Get new config with updated timeout
		configWithShortTimeout = lockConfig(LOCK_NAME1, Duration.ofMillis(2), Duration.ZERO);
		assertUnlocked(configWithShortTimeout.getName());
	}
}