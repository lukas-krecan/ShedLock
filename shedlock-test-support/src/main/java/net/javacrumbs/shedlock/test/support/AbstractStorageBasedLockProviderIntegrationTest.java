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
package net.javacrumbs.shedlock.test.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.junit.jupiter.api.Test;

public abstract class AbstractStorageBasedLockProviderIntegrationTest
        extends AbstractExtensibleLockProviderIntegrationTest {

    @Override
    protected abstract StorageBasedLockProvider getLockProvider();

    @Test
    public void lockShouldSurviveCacheClearingInTheMiddle() {
        StorageBasedLockProvider provider = getLockProvider();

        LockConfiguration configuration = lockConfig(LOCK_NAME1);
        Optional<SimpleLock> lock = provider.lock(configuration);
        assertThat(lock).isPresent();

        provider.clearCache();

        // lock is still locked
        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isEmpty();

        lock.get().unlock();
    }

    @Test
    public void unlockedLockShouldSurviveCacheClearingInTheMiddle() {
        StorageBasedLockProvider provider = getLockProvider();

        LockConfiguration configuration = lockConfig(LOCK_NAME1);

        Optional<SimpleLock> lock1 = provider.lock(configuration);
        assertThat(lock1).isPresent();
        lock1.get().unlock();

        provider.clearCache();

        // Lock can be obtained
        Optional<SimpleLock> lock2 = provider.lock(lockConfig(LOCK_NAME1));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }
}
