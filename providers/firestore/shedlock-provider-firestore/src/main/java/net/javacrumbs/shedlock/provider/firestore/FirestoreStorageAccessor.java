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
package net.javacrumbs.shedlock.provider.firestore;

import static java.util.Objects.requireNonNull;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.Transaction;
import com.google.cloud.firestore.WriteResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.Nullable;

public class FirestoreStorageAccessor extends AbstractStorageAccessor {
    private final Firestore firestore;
    private final String hostname;
    private final String collectionName;
    private final FirestoreLockProvider.FieldNames fieldNames;

    public FirestoreStorageAccessor(FirestoreLockProvider.Configuration configuration) {
        requireNonNull(configuration);
        this.firestore = configuration.getFirestore();
        this.hostname = Utils.getHostname();
        this.collectionName = configuration.getCollectionName();
        this.fieldNames = configuration.getFieldNames();
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        return insert(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        return updateExisting(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        updateOwn(lockConfiguration.getName(), lockConfiguration.getUnlockTime());
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        return updateOwn(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }

    private boolean insert(String name, Instant until) {
        try {
            return firestore.runTransaction(transaction -> {
                        DocumentReference docRef = getDocumentReference(name);
                        DocumentSnapshot snapshot = transaction.get(docRef).get();

                        if (snapshot.exists()) {
                            return false; // Document already exists
                        }

                        Map<String, Object> data = createLockData(until);
                        transaction.set(docRef, data);
                        return true;
                    })
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Thread interrupted while inserting record", e);
            return false;
        } catch (ExecutionException e) {
            logger.debug("Exception thrown when inserting record", e.getCause());
            return false;
        }
    }

    private boolean updateExisting(String name, Instant until) {
        try {
            return firestore.runTransaction(transaction -> {
                        DocumentReference docRef = getDocumentReference(name);
                        DocumentSnapshot snapshot = transaction.get(docRef).get();

                        if (!snapshot.exists()) {
                            return false; // Document doesn't exist
                        }

                        Timestamp lockUntilTimestamp = snapshot.getTimestamp(fieldNames.lockUntil());
                        if (lockUntilTimestamp != null && lockUntilTimestamp.toDate().toInstant().isAfter(ClockProvider.now())) {
                            return false; // Lock is still valid
                        }

                        Map<String, Object> data = createLockData(until);
                        transaction.set(docRef, data);
                        return true;
                    })
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Thread interrupted while updating record", e);
            return false;
        } catch (ExecutionException e) {
            logger.debug("Exception thrown when updating record", e.getCause());
            return false;
        }
    }

    private boolean updateOwn(String name, Instant until) {
        try {
            return firestore.runTransaction(transaction -> {
                        DocumentReference docRef = getDocumentReference(name);
                        DocumentSnapshot snapshot = transaction.get(docRef).get();

                        if (!snapshot.exists()) {
                            return false; // Document doesn't exist
                        }

                        String lockedBy = snapshot.getString(fieldNames.lockedBy());
                        if (!hostname.equals(lockedBy)) {
                            return false; // Not locked by this instance
                        }

                        Timestamp lockUntilTimestamp = snapshot.getTimestamp(fieldNames.lockUntil());
                        if (lockUntilTimestamp == null || lockUntilTimestamp.toDate().toInstant().isBefore(ClockProvider.now())) {
                            return false; // Lock has expired
                        }

                        Map<String, Object> updateData = Map.of(
                                fieldNames.lockUntil(), Timestamp.of(java.sql.Timestamp.from(until))
                        );
                        transaction.update(docRef, updateData);
                        return true;
                    })
                    .get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Thread interrupted while updating own record", e);
            return false;
        } catch (ExecutionException e) {
            logger.debug("Exception thrown when updating own record", e.getCause());
            return false;
        }
    }

    public Optional<Lock> findLock(String name) {
        try {
            DocumentSnapshot snapshot = getDocumentReference(name).get().get();
            if (!snapshot.exists()) {
                return Optional.empty();
            }

            return Optional.of(new Lock(
                    name,
                    nullableTimestamp(snapshot, fieldNames.lockedAt()),
                    nullableTimestamp(snapshot, fieldNames.lockUntil()),
                    snapshot.getString(fieldNames.lockedBy())
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.debug("Thread interrupted while finding lock", e);
            return Optional.empty();
        } catch (ExecutionException e) {
            logger.debug("Exception thrown when finding lock", e.getCause());
            return Optional.empty();
        }
    }

    private DocumentReference getDocumentReference(String name) {
        return firestore.collection(collectionName).document(name);
    }

    private Map<String, Object> createLockData(Instant until) {
        return Map.of(
                fieldNames.lockUntil(), Timestamp.of(java.sql.Timestamp.from(until)),
                fieldNames.lockedAt(), Timestamp.of(java.sql.Timestamp.from(ClockProvider.now())),
                fieldNames.lockedBy(), hostname
        );
    }

    @Nullable
    private static Instant nullableTimestamp(DocumentSnapshot snapshot, String field) {
        Timestamp timestamp = snapshot.getTimestamp(field);
        return timestamp != null ? timestamp.toDate().toInstant() : null;
    }

    public record Lock(
            String name, @Nullable Instant lockedAt, @Nullable Instant lockedUntil, @Nullable String lockedBy) {}
}
