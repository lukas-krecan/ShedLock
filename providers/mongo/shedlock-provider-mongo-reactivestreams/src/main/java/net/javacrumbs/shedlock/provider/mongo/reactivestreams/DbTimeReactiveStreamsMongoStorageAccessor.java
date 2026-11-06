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
import static com.mongodb.client.model.Filters.exists;
import static com.mongodb.client.model.Filters.expr;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.set;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.ID;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.LOCK_UNTIL;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.execute;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoServerException;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.reactivestreams.client.MongoCollection;
import java.time.Duration;
import java.util.List;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.StorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import org.bson.Document;
import org.bson.conversions.Bson;

class DbTimeReactiveStreamsMongoStorageAccessor implements StorageAccessor {
    private static final String DB_NOW = "$$NOW";

    private final MongoCollection<Document> collection;
    private final String hostname = Utils.getHostname();

    DbTimeReactiveStreamsMongoStorageAccessor(MongoCollection<Document> collection) {
        this.collection = collection;
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        try {
            // $expr is not allowed in upsert filters. Only initialize a document without lockUntil;
            // an existing lock causes a duplicate key error and is handled by updateRecord instead.
            execute(collection.findOneAndUpdate(
                    and(eq(ID, lockConfiguration.getName()), exists(LOCK_UNTIL, false)),
                    lockUpdate(lockConfiguration),
                    new FindOneAndUpdateOptions().upsert(true)));
            return true;
        } catch (MongoServerException e) {
            if (ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY) {
                return false;
            }
            throw new LockException(e);
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        // $$NOW is evaluated by the server and is constant throughout the operation.
        return execute(collection.findOneAndUpdate(
                        and(eq(ID, lockConfiguration.getName()), expr(compare("$lte", LOCK_UNTIL, DB_NOW))),
                        lockUpdate(lockConfiguration)))
                != null;
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        return execute(collection.findOneAndUpdate(
                        and(
                                eq(ID, lockConfiguration.getName()),
                                expr(compare("$gt", LOCK_UNTIL, DB_NOW)),
                                eq(LOCKED_BY, hostname)),
                        List.of(set(LOCK_UNTIL, dbNowPlus(lockConfiguration.getLockAtMostFor())))))
                != null;
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        // Keep the lock until lockedAt + lockAtLeastFor or the current server time, whichever is later.
        Document lockAtLeastUntil = new Document(
                "$add",
                List.of(field(LOCKED_AT), lockConfiguration.getLockAtLeastFor().toMillis()));
        execute(collection.findOneAndUpdate(
                eq(ID, lockConfiguration.getName()),
                List.of(set(LOCK_UNTIL, new Document("$max", List.of(lockAtLeastUntil, DB_NOW))))));
    }

    private List<Bson> lockUpdate(LockConfiguration lockConfiguration) {
        return List.of(combine(
                set(LOCK_UNTIL, dbNowPlus(lockConfiguration.getLockAtMostFor())),
                set(LOCKED_AT, DB_NOW),
                set(LOCKED_BY, literal(hostname))));
    }

    private static Document dbNowPlus(Duration duration) {
        return new Document("$add", List.of(DB_NOW, duration.toMillis()));
    }

    private static Document compare(String operator, String fieldName, Object value) {
        return new Document(operator, List.of(field(fieldName), value));
    }

    private static String field(String fieldName) {
        return "$" + fieldName;
    }

    private static Document literal(Object value) {
        return new Document("$literal", value);
    }
}
