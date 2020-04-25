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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.test.dunit.DUnitEnv;
import org.apache.geode.test.dunit.Host;
import org.apache.geode.test.dunit.standalone.DUnitLauncher;
import org.apache.geode.test.junit.rules.ServerStarterRule;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;

import java.util.Optional;

import static org.apache.geode.distributed.ConfigurationProperties.SERIALIZABLE_OBJECT_FILTER;
import static org.assertj.core.api.Assertions.assertThat;

public class GeodeLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static GeodeLockProvider lockProvider;

    @BeforeAll
    public static void startServer() throws InterruptedException {
        DUnitLauncher.launchIfNeeded(1);
        Host.getHost(0).getVM(0).invokeAsync(() -> {
            ServerStarterRule serverStarterRule = GeodeTestUtils.getServerStartupRule(new ServerStarterRule(),0);
            serverStarterRule.startServer();
        });
        ClientCache clientCache = new ClientCacheFactory()
            .addPoolServer("localhost",40404)
            .create();
        Thread.sleep(5000);
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
       if(lock.isPresent()){
           lock.get().unlock();
           //Means it was already unlocked
           Assert.assertTrue("Lock was unlocked",true);
       } else {
           Assert.fail("Lock wasnt unlocked");
       }

   }

    @Override
    protected void assertLocked(final String lockName) {
        LockConfiguration lockConfiguration = GeodeTestUtils.simpleLockConfig(lockName, 1);
        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);
        assertThat(lock).isEmpty();
    }
}


