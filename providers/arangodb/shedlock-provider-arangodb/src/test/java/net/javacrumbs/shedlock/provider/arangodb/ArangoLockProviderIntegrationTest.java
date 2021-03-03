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
package net.javacrumbs.shedlock.provider.arangodb;

import com.arangodb.ArangoCollection;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.COLLECTION_NAME;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;


@Testcontainers
public class ArangoLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    @Container
    public static final ArangoContainer arangoContainer = new ArangoContainer();

    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "";
    private static final String DB_HOSTNAME = "localhost";
    private static final int DB_PORT = 8529;

    private static final String DB_NAME = "arangodb";

    private static ArangoDB arango;
    private static ArangoDatabase arangoDatabase;
    private static ArangoCollection arangoCollection;

    @BeforeAll
    static void beforeAll() {
        arangoContainer.start();

        arango = new ArangoDB.Builder()
            .host(DB_HOSTNAME, arangoContainer.getMappedPort(DB_PORT))
            .user(DB_USER)
            .password(DB_PASSWORD)
            .useSsl(false)
            .build();

        if (!arango.getDatabases().contains(DB_NAME)) {
            arango.createDatabase(DB_NAME);
        }

        arangoDatabase = arango.db(DB_NAME);

        if (arangoDatabase.getCollections().stream()
            .anyMatch(collectionEntity -> collectionEntity.getName().equals(COLLECTION_NAME))) {
            arangoCollection = arangoDatabase.collection(COLLECTION_NAME);
            arangoCollection.drop();
        }
    }

    @AfterAll
    static void afterAll() {
        arango.db(DB_NAME).drop();
        arango.shutdown();
        arangoContainer.stop();
    }

    @BeforeEach
    void setUp() {
        arangoDatabase.createCollection(COLLECTION_NAME);
        arangoCollection = arangoDatabase.collection(COLLECTION_NAME);
    }

    @AfterEach
    void tearDown() {
        arangoCollection.drop();
    }

    @Override
    protected LockProvider getLockProvider() {
        return new ArangoLockProvider(arangoDatabase);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        BaseDocument document = getDocument(lockName);
        Instant instantLockedAt = getInstant(document, LOCKED_AT);
        Instant instantLockUntil = getInstant(document, LOCK_UNTIL);

        assertThat(document.getAttribute(LOCKED_BY).toString()).isNotEmpty();
        assertThat(instantLockedAt).isBeforeOrEqualTo(now());
        assertThat(instantLockUntil).isBeforeOrEqualTo(now());
    }

    private Instant getInstant(BaseDocument document, String lockedAt) {
        return Instant.parse(document.getAttribute(lockedAt).toString());
    }

    @Override
    protected void assertLocked(String lockName) {
        BaseDocument document = getDocument(lockName);

        Instant instantLockedAt = getInstant(document, LOCKED_AT);
        Instant instantLockUntil = getInstant(document, LOCK_UNTIL);

        assertThat(document.getAttribute(LOCKED_BY).toString()).isNotEmpty();
        assertThat(instantLockedAt).isBeforeOrEqualTo(now());
        assertThat(instantLockUntil).isAfter(now());
    }

    private BaseDocument getDocument(String lockName) {
        return arangoCollection.getDocument(lockName, BaseDocument.class);
    }

    private static class ArangoContainer extends GenericContainer<ArangoContainer> {
        ArangoContainer() {
            super("arangodb/arangodb:3.7.2");
            withEnv("ARANGO_NO_AUTH", "1");
            withLogConsumer(outputFrame -> logger().info(outputFrame.getUtf8String()));
            addExposedPort(DB_PORT);
            setCommand("arangod", "--server.endpoint", "tcp://0.0.0.0:8529");
            waitingFor(Wait.forLogMessage(".*is ready for business. Have fun!.*", 1));
        }
    }
}
