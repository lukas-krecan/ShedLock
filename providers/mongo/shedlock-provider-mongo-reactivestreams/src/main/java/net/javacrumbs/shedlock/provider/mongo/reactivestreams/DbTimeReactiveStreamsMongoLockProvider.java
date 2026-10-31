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

import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.DEFAULT_SHEDLOCK_COLLECTION_NAME;

import com.mongodb.ReadConcern;
import com.mongodb.WriteConcern;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.mongodb.reactivestreams.client.MongoDatabase;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.bson.Document;

/**
 * Distributed lock using the Mongo server clock. Requires MongoDB &gt;= 4.2.
 *
 * <p>Uses the same collection and document format as {@link ReactiveStreamsMongoLockProvider}.
 * Existing lock names are cached, so externally deleted documents are not automatically recreated
 * while their names remain cached. Use {@link #clearCache()} to clear the cache.
 */
public class DbTimeReactiveStreamsMongoLockProvider extends StorageBasedLockProvider {
    /** Uses the default {@code shedLock} collection with majority read and write concerns. */
    public DbTimeReactiveStreamsMongoLockProvider(MongoDatabase mongoDatabase) {
        this(mongoDatabase
                .getCollection(DEFAULT_SHEDLOCK_COLLECTION_NAME)
                .withWriteConcern(WriteConcern.MAJORITY)
                .withReadConcern(ReadConcern.MAJORITY));
    }

    /**
     * Uses the supplied collection. Please, make sure that the collection has {@link WriteConcern#MAJORITY} set.
     *
     * @param collection Mongo collection to be used
     */
    public DbTimeReactiveStreamsMongoLockProvider(MongoCollection<Document> collection) {
        super(new DbTimeReactiveStreamsMongoStorageAccessor(collection));
    }
}
