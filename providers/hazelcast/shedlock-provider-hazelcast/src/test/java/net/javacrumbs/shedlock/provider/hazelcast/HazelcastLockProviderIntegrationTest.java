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
package net.javacrumbs.shedlock.provider.hazelcast;


import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static HazelcastInstance hazelcastInstance;

    private static HazelcastLockProvider lockProvider;

    @BeforeClass
    public static void startHazelcast() throws IOException {
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        lockProvider = new HazelcastLockProvider(hazelcastInstance);
    }

    @AfterClass
    public static void stopHazelcast() throws IOException {
        hazelcastInstance.shutdown();
    }

    @After
    public void resetLockProvider() throws UnknownHostException {
        hazelcastInstance.removeDistributedObjectListener(HazelcastLockProvider.LOCK_STORE_KEY_DEFAULT);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        assertThat(isUnlocked(lockName));
    }

    private boolean isUnlocked(final String lockName) {
        final Instant now = Instant.now();
        final HazelcastLock lock = lockProvider.getLock(lockName);
        return lock == null;
    }

    @Override
    protected void assertLocked(final String lockName) {
        assertThat(!isUnlocked(lockName));
    }


}