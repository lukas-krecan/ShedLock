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
import com.arangodb.ArangoDBException;
import com.arangodb.ArangoDatabase;
import com.arangodb.entity.BaseDocument;
import com.arangodb.entity.DocumentCreateEntity;
import com.arangodb.entity.StreamTransactionEntity;
import com.arangodb.model.DocumentCreateOptions;
import com.arangodb.model.DocumentReadOptions;
import com.arangodb.model.DocumentUpdateOptions;
import com.arangodb.model.StreamTransactionOptions;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.Utils;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Instant;
import java.util.Optional;

import static net.javacrumbs.shedlock.support.Utils.getHostname;

/**
 * Arango Lock Provider needs existing collection
 * <br>
 * Example creating a collection through init scripts (javascript)
 * <pre>
 * db._useDatabase("DB_NAME");
 * db._create("COLLECTION_NAME");
 * </pre>
 */
public class ArangoLockProvider implements LockProvider {

    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String COLLECTION_NAME = "shedLock";

    private final ArangoCollection arangoCollection;

    /**
     * Instantiates a new Arango lock provider.
     *
     * @param arangoDatabase the arango database
     */
    public ArangoLockProvider(@NonNull ArangoDatabase arangoDatabase) {
        this(arangoDatabase.collection(COLLECTION_NAME));
    }

    /**
     * Instantiates a new Arango lock provider.
     *
     * @param arangoCollection the arango collection
     */
    public ArangoLockProvider(@NonNull ArangoCollection arangoCollection) {
        this.arangoCollection = arangoCollection;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String transactionId = null;

        try {
            /*
                Transaction is necessary because repsert (insert with overwrite=true in arangodb)
                is not possible with condition check (see case 2 description below)
             */
            StreamTransactionEntity streamTransactionEntity = arangoCollection.db().beginStreamTransaction(
                new StreamTransactionOptions().exclusiveCollections(arangoCollection.name())
            );

            transactionId = streamTransactionEntity.getId();

            /*  There are three possible situations:
                1. The lock document does not exist yet - insert document
                2. The lock document exists and lockUtil <= now - update document
                3. The lock document exists and lockUtil > now - nothing to do
             */
            BaseDocument existingDocument = arangoCollection.getDocument(lockConfiguration.getName(),
                BaseDocument.class, new DocumentReadOptions().streamTransactionId(transactionId));

            // 1. case
            if (existingDocument == null) {
                BaseDocument newDocument = insertNewLock(transactionId, lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
                return Optional.of(new ArangoLock(arangoCollection, newDocument, lockConfiguration));
            }

            // 2. case
            Instant lockUntil = Instant.parse(existingDocument.getAttribute(LOCK_UNTIL).toString());
            if (lockUntil.compareTo(ClockProvider.now()) <= 0) {
                updateLockAtMostUntil(transactionId, existingDocument, lockConfiguration.getLockAtMostUntil());
                return Optional.of(new ArangoLock(arangoCollection, existingDocument, lockConfiguration));
            }

            // 3. case
            return Optional.empty();

        } catch (ArangoDBException e) {
            if (transactionId != null) {
                arangoCollection.db().abortStreamTransaction(transactionId);
            }
            throw new LockException("Unexpected error occured", e);
        } finally {
            if (transactionId != null) {
                arangoCollection.db().commitStreamTransaction(transactionId);
            }
        }
    }

    private BaseDocument insertNewLock(String transactionId, String documentKey, Instant lockAtMostUntil) throws ArangoDBException {
        BaseDocument newDocument = new BaseDocument();
        newDocument.setKey(documentKey);
        setDocumentAttributes(newDocument, lockAtMostUntil);
        DocumentCreateEntity<BaseDocument> document = arangoCollection.insertDocument(newDocument,
            new DocumentCreateOptions().streamTransactionId(transactionId).returnNew(true)
        );
        return document.getNew();
    }

    private void updateLockAtMostUntil(String transactionId,
                                       BaseDocument existingDocument,
                                       Instant lockAtMostUntil) {
        setDocumentAttributes(existingDocument, lockAtMostUntil);
        arangoCollection.updateDocument(existingDocument.getKey(), existingDocument,
            new DocumentUpdateOptions().streamTransactionId(transactionId));
    }

    private void setDocumentAttributes(BaseDocument baseDocument, Instant lockAtMostUntil) {
        baseDocument.addAttribute(LOCK_UNTIL, Utils.toIsoString(lockAtMostUntil));
        baseDocument.addAttribute(LOCKED_AT, Utils.toIsoString(ClockProvider.now()));
        baseDocument.addAttribute(LOCKED_BY, getHostname());
    }

    private static final class ArangoLock extends AbstractSimpleLock {

        private final ArangoCollection arangoCollection;
        private final BaseDocument document;

        public ArangoLock(final ArangoCollection arangoCollection,
                          final BaseDocument document,
                          final LockConfiguration lockConfiguration) {

            super(lockConfiguration);
            this.arangoCollection = arangoCollection;
            this.document = document;
        }

        @Override
        protected void doUnlock() {
            try {
                document.addAttribute(LOCK_UNTIL, Utils.toIsoString(lockConfiguration.getUnlockTime()));
                arangoCollection.updateDocument(lockConfiguration.getName(), document);
            } catch (ArangoDBException e) {
                throw new LockException("Unexpected error occured", e);
            }

        }

    }

}
