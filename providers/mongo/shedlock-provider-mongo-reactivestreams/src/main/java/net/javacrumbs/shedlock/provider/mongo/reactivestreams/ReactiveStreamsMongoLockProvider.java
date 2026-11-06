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
package net.javacrumbs.shedlock.provider.mongo.reactivestreams;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoServerException;
import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import java.time.Instant;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.Utils;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

/**
 * Distributed lock using Reactive MongoDB. Requires
 * mongodb-driver-reactivestreams
 *
 * <p>
 * It uses a collection that contains documents like this:
 *
 * <pre>
 * {
 *    "_id" : "lock name",
 *    "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
 *    "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
 *    "lockedBy" : "host name"
 * }
 * </pre>
 *
 * lockedAt is used for troubleshooting. lockedBy also identifies the owner when extending a lock.
 *
 * <ol>
 * <li>Upserts a document using the filter _id == name AND lockUntil &lt;= now.
 * <li>If the document is inserted or an expired lock is updated, we have the lock.
 * <li>If a valid lock already exists, the insert fails with a duplicate key error and the lock is unavailable.
 * <li>When unlocking, lockUntil is set to now or lockAtLeastUntil, whichever is later.
 * </ol>
 *
 * <p>Uses the application server clock. To use the Mongo server clock, use {@link #create(MongoDatabase, boolean)} with {@code useDbTime} set to {@code true}.
 */
public class ReactiveStreamsMongoLockProvider implements ExtensibleLockProvider {
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String ID = "_id";
    static final String DEFAULT_SHEDLOCK_COLLECTION_NAME = "shedLock";

    private final String hostname;
    private final MongoCollection<Document> collection;

    /**
     * Creates a lock provider using the default {@code shedLock} collection with majority read and write concerns.
     *
     * <p>Using the Mongo server clock requires MongoDB &gt;= 4.2. In this mode, existing lock names are cached,
     * so externally deleted documents are not automatically recreated while their names remain cached.
     * The cache can be cleared by casting the returned provider to
     * {@link net.javacrumbs.shedlock.support.StorageBasedLockProvider} and calling
     * {@link net.javacrumbs.shedlock.support.StorageBasedLockProvider#clearCache()}.
     *
     * @param mongoDatabase Mongo database to be used
     * @param useDbTime {@code true} to use the Mongo server clock, {@code false} to use the application server clock
     * @return a lock provider using the selected clock
     */
    public static ExtensibleLockProvider create(MongoDatabase mongoDatabase, boolean useDbTime) {
        return create(getCollection(mongoDatabase), useDbTime);
    }

    /**
     * Creates a lock provider using the supplied collection. Please, make sure that the collection has
     * {@link WriteConcern#MAJORITY} set.
     *
     * <p>See {@link #create(MongoDatabase, boolean)} for MongoDB version requirements and lock name caching
     * when using the Mongo server clock.
     *
     * @param collection Mongo collection to be used
     * @param useDbTime {@code true} to use the Mongo server clock, {@code false} to use the application server clock
     * @return a lock provider using the selected clock
     */
    public static ExtensibleLockProvider create(MongoCollection<Document> collection, boolean useDbTime) {
        if (useDbTime) {
            return new DbTimeReactiveStreamsMongoLockProvider(collection);
        } else {
            return new ReactiveStreamsMongoLockProvider(collection);
        }
    }

    /** Uses the application server clock and the default {@code shedLock} collection with majority read and write concerns. */
    public ReactiveStreamsMongoLockProvider(MongoDatabase mongoDatabase) {
        this(getCollection(mongoDatabase));
    }

    private static MongoCollection<Document> getCollection(MongoDatabase mongoDatabase) {
        return mongoDatabase
                .getCollection(DEFAULT_SHEDLOCK_COLLECTION_NAME)
                .withWriteConcern(WriteConcern.MAJORITY)
                .withReadConcern(ReadConcern.MAJORITY);
    }

    /**
     * Uses the application server clock and the supplied collection. Please, make sure that the collection has
     * {@link WriteConcern#MAJORITY} set.
     * @param collection
     *            Mongo collection to be used
     */
    public ReactiveStreamsMongoLockProvider(MongoCollection<Document> collection) {
        this.collection = collection;
        this.hostname = Utils.getHostname();
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Instant now = now();
        Bson update = combine(
                set(LOCK_UNTIL, lockConfiguration.getLockAtMostUntil()), set(LOCKED_AT, now), set(LOCKED_BY, hostname));
        try {
            // There are three possible situations:
            // 1. The lock document does not exist yet - it is inserted - we have the lock
            // 2. The lock document exists and lockUtil <= now - it is updated - we have the
            // lock
            // 3. The lock document exists and lockUtil > now - Duplicate key exception is
            // thrown
            execute(getCollection()
                    .findOneAndUpdate(
                            and(eq(ID, lockConfiguration.getName()), lte(LOCK_UNTIL, now)),
                            update,
                            new FindOneAndUpdateOptions().upsert(true)));
            return Optional.of(new ReactiveMongoLock(lockConfiguration, this));
        } catch (MongoServerException e) {
            if (ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY) {
                // Upsert attempts to insert when there were no filter matches.
                // This means there was a lock with matching ID with lockUntil > now.
                return Optional.empty();
            } else {
                throw new LockException(e);
            }
        }
    }

    private Optional<SimpleLock> extend(LockConfiguration lockConfiguration) {
        Instant now = now();
        Bson update = set(LOCK_UNTIL, lockConfiguration.getLockAtMostUntil());

        Document updatedDocument = execute(getCollection()
                .findOneAndUpdate(
                        and(eq(ID, lockConfiguration.getName()), gt(LOCK_UNTIL, now), eq(LOCKED_BY, hostname)),
                        update));

        if (updatedDocument != null) {
            return Optional.of(new ReactiveMongoLock(lockConfiguration, this));
        } else {
            return Optional.empty();
        }
    }

    private void unlock(LockConfiguration lockConfiguration) {
        // Set lockUtil to now or lockAtLeastUntil whichever is later
        execute(getCollection()
                .findOneAndUpdate(
                        eq(ID, lockConfiguration.getName()),
                        combine(set(LOCK_UNTIL, lockConfiguration.getUnlockTime()))));
    }

    static <T> @Nullable T execute(Publisher<T> command) {
        SingleLockableSubscriber<T> subscriber = new SingleLockableSubscriber<>();
        command.subscribe(subscriber);
        subscriber.await();
        Throwable error = subscriber.getError();
        if (error != null) {
            if (error instanceof RuntimeException runtimeException) {
                throw runtimeException;
            } else {
                throw new LockException("Error when executing Mongo statement", error);
            }
        } else {
            return subscriber.getValue();
        }
    }

    private MongoCollection<Document> getCollection() {
        return collection;
    }

    private Instant now() {
        return ClockProvider.now();
    }

    private static final class ReactiveMongoLock extends AbstractSimpleLock {
        private final ReactiveStreamsMongoLockProvider mongoLockProvider;

        private ReactiveMongoLock(
                LockConfiguration lockConfiguration, ReactiveStreamsMongoLockProvider mongoLockProvider) {
            super(lockConfiguration);
            this.mongoLockProvider = mongoLockProvider;
        }

        @Override
        public void doUnlock() {
            mongoLockProvider.unlock(lockConfiguration);
        }

        @Override
        public Optional<SimpleLock> doExtend(LockConfiguration newLockConfiguration) {
            return mongoLockProvider.extend(newLockConfiguration);
        }
    }
}
