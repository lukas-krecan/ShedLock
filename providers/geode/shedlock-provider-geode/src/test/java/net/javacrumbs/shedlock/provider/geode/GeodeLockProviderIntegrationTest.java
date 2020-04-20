/**
 * Copyright 2009-2020 the original author or authors.
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

package net.javacrumbs.shedlock.provider.geode;

import junitparams.JUnitParamsRunner;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.DistributedRule;
import org.apache.geode.test.dunit.rules.MemberVM;
import org.junit.ClassRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.runner.RunWith;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(JUnitParamsRunner.class)
public class GeodeLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static GeodeLockProvider lockProvider;

    @ClassRule
    public static final ClusterStartupRule clusterStartupRule = new ClusterStartupRule(1).withLogFile();

    @ClassRule
    public static final DistributedRule distributedRule = new DistributedRule(1);

    @BeforeAll
    public static void startServer() {
        MemberVM server = GeodeTestUtils.startServer(clusterStartupRule, 1);
        ClientCache clientCache = new ClientCacheFactory()
            .addPoolLocator("localhost",GeodeTestUtils.getLocatorPort()).create();
        lockProvider = new GeodeLockProvider(clientCache);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

   @Override
    protected void assertUnlocked(final String lockName) {
       LockConfiguration lockConfiguration = GeodeTestUtils.simpleLockConfig(lockName, 1);
       Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);
       assertThat(lock).isEmpty();
   }

    @Override
    protected void assertLocked(final String lockName) {
        LockConfiguration lockConfiguration = GeodeTestUtils.simpleLockConfig(lockName, 1);
        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);
        assertThat(lock).isNotEmpty();
    }
}


