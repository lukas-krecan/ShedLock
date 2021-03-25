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
package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.ecwid.consul.v1.session.model.Session;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class ConsulLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final MyConsulContainer consul = new MyConsulContainer();

    public static ConsulClient consulClient;

    @BeforeAll
    public static void startConsul() {
        consulClient = new ConsulClient(consul.getHost(), consul.getFirstMappedPort());
    }

    @AfterEach
    public void checkSessions() {
        Response<List<Session>> sessionListResponse = consulClient.getSessionList(QueryParams.DEFAULT);
        assertThat(sessionListResponse.getValue())
            .as("There should no sessions remain in consul after all locks have been released.")
            .isEmpty();
    }

    @Override
    protected LockProvider getLockProvider() {
        return new ConsulLockProvider(
            ConsulLockProvider.Configuration.builder()
                .withConsulClient(consulClient).build()
        );
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        GetValue leader = getLockValue(lockName);
        assertThat(leader).isNull();
    }

    @Override
    protected void assertLocked(final String lockName) {
        GetValue leader = getLockValue(lockName);
        assertThat(Optional.ofNullable(leader).map(GetValue::getSession)).isNotEmpty();
    }

    private GetValue getLockValue(String lockName) {
        return consulClient.getKVValue(lockName + "-leader").getValue();
    }

    @Test
    @Override
    public void shouldTimeout() throws InterruptedException {
        // as consul has 10 seconds ttl minimum and has double ttl unlocking time, you have to wait for 20 seconds for the unlock time.
        Duration lockAtMostFor = Duration.ofSeconds(11);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.multipliedBy(2).toMillis() + 100);
        assertUnlocked(LOCK_NAME1);

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig(LOCK_NAME1, Duration.ofMillis(50), Duration.ZERO));
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Test
    public void shouldNotTimeoutIfLessThanMinTtlPassed() throws InterruptedException {
        Duration lockAtMostFor = Duration.ofSeconds(1);
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, lockAtMostFor, Duration.ZERO);
        Optional<SimpleLock> lock1 = getLockProvider().lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        sleep(lockAtMostFor.multipliedBy(2).toMillis() + 100);
        assertLocked(LOCK_NAME1);

        // release lock to satisfy condition for #checkSessions()
        lock1.get().unlock();
    }

    private static class MyConsulContainer extends GenericContainer<MyConsulContainer> {
        MyConsulContainer() {
            super("consul:1.9");
            withNetworkAliases("myconsul")
                .withExposedPorts(8500)
                .waitingFor(Wait.forLogMessage(".*Synced node info.*", 1))
                .withCommand(
                    "agent",
                    "-dev",
                    "-server",
                    "-bootstrap",
                    "-client",
                    "0.0.0.0",
                    "-log-level",
                    "trace"
                );
        }
    }
}
