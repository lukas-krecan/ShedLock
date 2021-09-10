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
package net.javacrumbs.shedlock.provider.mongo.reactivestreams;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.mongodb.reactivestreams.client.MongoCollection;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.distribution.Version;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractExtensibleLockProviderIntegrationTest;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Date;

import static com.mongodb.client.model.Filters.eq;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.DEFAULT_SHEDLOCK_COLLECTION_NAME;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.ID;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

public class ReactiveStreamsMongoLockProviderIntegrationTest extends AbstractExtensibleLockProviderIntegrationTest {
    private static final MongodStarter starter = MongodStarter.getDefaultInstance();

    private static final String DB_NAME = "db";

    private static MongodExecutable mongodExe;
    private static MongodProcess mongod;

    private static MongoClient mongo;

    @BeforeAll
    public static void startMongo() throws IOException {
        mongodExe = starter.prepare(MongodConfig.builder()
            .version(Version.Main.V3_6)
            .build());
        mongod = mongodExe.start();

        mongo = MongoClients.create("mongodb://localhost:" + mongod.getConfig().net().getPort());
    }

    @AfterAll
    public static void stopMongo() {
        mongo.close();
        mongod.stop();
        mongodExe.stop();
    }


    @BeforeEach
    public void cleanDb() {
        execute(mongo.getDatabase(DB_NAME).drop());
    }

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return new ReactiveStreamsMongoLockProvider(mongo.getDatabase(DB_NAME));
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Document lockDocument = getLockDocument(lockName);
        assertThat((Date) lockDocument.get(LOCK_UNTIL)).isBeforeOrEqualsTo(now());
        assertThat((Date) lockDocument.get(LOCKED_AT)).isBeforeOrEqualsTo(now());
        assertThat((String) lockDocument.get(LOCKED_BY)).isNotEmpty();
    }

    private Date now() {
        return new Date();
    }

    @Override
    protected void assertLocked(String lockName) {
        Document lockDocument = getLockDocument(lockName);
        assertThat((Date) lockDocument.get(LOCK_UNTIL)).isAfter(now());
        assertThat((Date) lockDocument.get(LOCKED_AT)).isBeforeOrEqualsTo(now());
        assertThat((String) lockDocument.get(LOCKED_BY)).isNotEmpty();
    }

    private MongoCollection<Document> getLockCollection() {
        return mongo.getDatabase(DB_NAME).getCollection(DEFAULT_SHEDLOCK_COLLECTION_NAME);
    }

    private Document getLockDocument(String lockName) {
        return execute(getLockCollection().find(eq(ID, lockName)).first());
    }

    @Test
    public void shouldLockWhenDocumentRemovedExternally() {
        LockProvider provider = getLockProvider();
        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);

        DeleteResult result = execute(getLockCollection().deleteOne(eq(ID, LOCK_NAME1)));

        assumeThat(result.getDeletedCount()).isEqualTo(1);

        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);
    }
}
