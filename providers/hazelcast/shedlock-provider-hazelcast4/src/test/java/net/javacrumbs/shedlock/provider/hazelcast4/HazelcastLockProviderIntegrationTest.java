/**
 * Copyright 2009 the original author or authors.
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
package net.javacrumbs.shedlock.provider.hazelcast4;


import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static HazelcastInstance hazelcastInstance;

    private static HazelcastLockProvider lockProvider;

    @BeforeAll
    public static void startHazelcast() {
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        lockProvider = new HazelcastLockProvider(hazelcastInstance);
    }

    @AfterAll
    public static void stopHazelcast() {
        hazelcastInstance.shutdown();
    }

    @AfterEach
    public void resetLockProvider() {
        // hazelcastInstance.removeDistributedObjectListener(HazelcastLockProvider.LOCK_STORE_KEY_DEFAULT);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

   @Override
    protected void assertUnlocked(final String lockName) { assertThat(isUnlocked(lockName)).isTrue(); }

    private boolean isUnlocked(final String lockName) {
        final HazelcastLock lock = lockProvider.getLock(lockName);
        return lock == null || lock.isExpired(ClockProvider.now());
    }

    @Override
    protected void assertLocked(final String lockName) {
        assertThat(isUnlocked(lockName)).isFalse();
    }
}
