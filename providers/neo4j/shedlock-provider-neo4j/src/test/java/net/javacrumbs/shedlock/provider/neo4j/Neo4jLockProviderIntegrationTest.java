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
package net.javacrumbs.shedlock.provider.neo4j;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import net.javacrumbs.shedlock.test.support.FuzzTester;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static java.lang.Thread.sleep;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class Neo4jLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    protected Neo4jTestUtils testUtils;
    protected MyNeo4jContainer container;
    private Driver driver;

    private static final String MY_LOCK = "my-lock";
    private static final String OTHER_LOCK = "other-lock";

    @BeforeAll
    public void startDb() {
        container = new MyNeo4jContainer();
        container.start();
        driver = GraphDatabase.driver(container.getBoltUrl(), AuthTokens.none());
    }

    @AfterAll
    public void shutDownDb() {
        container.stop();
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new Neo4jLockProvider(testUtils.getDriver());
    }

    @BeforeEach
    public void initTestUtils() {
        testUtils = new Neo4jTestUtils(driver);
    }

    @AfterEach
    public void cleanup() {
        testUtils.clean();
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Neo4jTestUtils.LockInfo lockInfo = getLockInfo(lockName);
        Instant now = ClockProvider.now();
        assertThat(lockInfo.getLockUntil()).describedAs("is unlocked").isBeforeOrEqualTo(now.truncatedTo(ChronoUnit.MILLIS).plusMillis(1));
    }

    @Override
    protected void assertLocked(String lockName) {
        Neo4jTestUtils.LockInfo lockInfo = getLockInfo(lockName);
        Instant now = ClockProvider.now();

        assertThat(lockInfo.getLockUntil()).describedAs(getClass().getName() + " is locked").isAfter(now);
    }

    @Test
    public void shouldCreateLockIfRecordAlreadyExists() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("name", LOCK_NAME1);
        parameters.put("previousLockTime", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        parameters.put("lockedBy", "me");
        testUtils.executeTransactionally("CREATE (lock:shedlock { name: $name, lock_until: $previousLockTime, locked_at: $previousLockTime, locked_by: $lockedBy })", parameters, null);
        assertUnlocked(LOCK_NAME1);
        shouldCreateLock();
    }

    @Test
    public void fuzzTestShouldWorkWithTransaction() throws ExecutionException, InterruptedException {
        new FuzzTester(getLockProvider()) {
            @Override
            protected Void task(int iterations, Job job) {
                try (
                    Session session = testUtils.getDriver().session();
                    Transaction transaction = session.beginTransaction()
                ) {
                    super.task(iterations, job);
                    transaction.commit();
                    return null;
                } catch (Throwable e) {
                    LoggerFactory.getLogger(getClass())
                        .error("Exception caught:", e);
                    return null;
                }
            }

            @Override
            protected boolean shouldLog() {
                return true;
            }
        }.doFuzzTest();
    }


    @Test
    void shouldNotUpdateOnInsertIfPreviousDidNotEndWhenNotUsingDbTime() {
        shouldNotUpdateOnInsertIfPreviousDidNotEnd();
    }

    @Test
    void shouldNotUpdateOnInsertIfPreviousDidNotEndWhenUsingDbTime() {
        shouldNotUpdateOnInsertIfPreviousDidNotEnd();
    }

    private void shouldNotUpdateOnInsertIfPreviousDidNotEnd() {
        Neo4jStorageAccessor accessor = getAccessor();

        assertThat(
            accessor.insertRecord(lockConfig(MY_LOCK, Duration.ofSeconds(10)))
        ).isEqualTo(true);

        Instant originalLockValidity = testUtils.getLockedUntil(MY_LOCK);

        assertThat(
            accessor.insertRecord(lockConfig(MY_LOCK, Duration.ofSeconds(10)))
        ).isEqualTo(false);

        assertThat(testUtils.getLockedUntil(MY_LOCK)).isEqualTo(originalLockValidity);
    }

    @Test
    void shouldNotUpdateOtherLockConfigurationsWhenNotUsingDbTime() throws InterruptedException {
        shouldNotUpdateOtherLockConfigurations();
    }

    @Test
    void shouldNotUpdateOtherLockConfigurationsWhenUsingDbTime() throws InterruptedException {
        shouldNotUpdateOtherLockConfigurations();
    }

    private void shouldNotUpdateOtherLockConfigurations() throws InterruptedException {
        Neo4jStorageAccessor accessor = getAccessor();

        Duration lockAtMostFor = Duration.ofMillis(10);
        assertThat(accessor.insertRecord(lockConfig(MY_LOCK, lockAtMostFor))).isEqualTo(true);
        assertThat(accessor.insertRecord(lockConfig(OTHER_LOCK, lockAtMostFor))).isEqualTo(true);

        Instant myLockLockedUntil = testUtils.getLockedUntil(MY_LOCK);
        Instant otherLockLockedUntil = testUtils.getLockedUntil(OTHER_LOCK);

        // wait for a while so there will be a difference in the timestamp
        // when system time is used seems there is no milliseconds in the timestamp so to make a difference we have to wait for at least a second
        sleep(1000);


        // act
        assertThat(accessor.updateRecord(new LockConfiguration(now(), MY_LOCK, lockAtMostFor, Duration.ZERO))).isEqualTo(true);


        // assert
        assertThat(testUtils.getLockedUntil(MY_LOCK)).isAfter(myLockLockedUntil);
        // check that the other lock has not been affected by "my-lock" update
        assertThat(testUtils.getLockedUntil(OTHER_LOCK)).isEqualTo(otherLockLockedUntil);
    }

    @NonNull
    private LockConfiguration lockConfig(String myLock, Duration lockAtMostFor) {
        return new LockConfiguration(now(), myLock, lockAtMostFor, Duration.ZERO);
    }

    @NonNull
    protected Neo4jStorageAccessor getAccessor() {
        return new Neo4jStorageAccessor(this.testUtils.getDriver(), "shedlock", null);
    }

    protected Neo4jTestUtils.LockInfo getLockInfo(String lockName) {
        return testUtils.getLockInfo(lockName);
    }

    private static class MyNeo4jContainer extends Neo4jContainer<MyNeo4jContainer> {
        MyNeo4jContainer() {
            super(DockerImageName.parse("neo4j").withTag("4.4.6"));
            withoutAuthentication();
        }
    }
}
