/**
 * Copyright 2009-2020 the original author or authors.
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

package net.javacrumbs.shedlock.provider.geode;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.junit.Assert;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

//@Testcontainers
public class GeodeLockProviderIntegrationTest2 extends AbstractLockProviderIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(GeodeLockProviderIntegrationTest2.class);

//    @Container
//    public static GeodeContainer container =
//        new GeodeContainer("apachegeode/geode")
//            .withClasspathResourceMapping("start-servers.gfsh", "start-servers.gfsh", BindMode.READ_ONLY)
//            .withCommand("gfsh","run","--file=start-servers.gfsh")
//            .withLogConsumer(outputFrame -> logger.info(outputFrame.getUtf8String()))
//            .withExposedPorts(1099, 8080, 7070, 10334, 40404)
//            .waitingFor(new org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy()
//                .withRegEx(".*Status    : PASSED.*\\s")
//                .withTimes(2)
//                .withStartupTimeout(Duration.of(60, SECONDS)));

    private static GeodeLockProvider lockProvider;

    @BeforeAll
    static void setUpGeode() {
        ClientCacheFactory cacheFactory = new ClientCacheFactory();
        cacheFactory.addPoolLocator("localhost", 10334).create();
        lockProvider = new GeodeLockProvider(cacheFactory.create());
    }


    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        LockConfiguration lockConfiguration = GeodeTestUtils.simpleLockConfig(lockName, 1);
        Optional<SimpleLock> lock = lockProvider.lock(lockConfiguration);
        if (lock.isPresent()) {
            lock.get().unlock();
            //Means it was already unlocked
            Assert.assertTrue("Lock was unlocked", true);
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

    static class GeodeContainer extends GenericContainer<GeodeContainer> {
        public GeodeContainer(String imageName) {
            super(imageName);
        }
    }
}


