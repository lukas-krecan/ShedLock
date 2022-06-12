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
package net.javacrumbs.shedlock.provider.opensearch;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.opensearch.OpenSearchException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.rest.RestStatus;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

/**
 * Lock using OpenSearch &gt;= .
 * Requires opensearch-rest-high-level-client &gt; 1.1.0
 * <p>
 * It uses a collection that contains documents like this:
 * <pre>
 * {
 *    "name" : "lock name",
 *    "lockUntil" :  {
 *      "type":   "date",
 *      "format": "epoch_millis"
 *    },
 *    "lockedAt" : {
 *      "type":   "date",
 *      "format": "epoch_millis"
 *    }:
 *    "lockedBy" : "hostname"
 * }
 * </pre>
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the code
 *
 * <ol>
 * <li>
 * Attempts to insert a new lock record. As an optimization, we keep in-memory track of created lock records. If the record
 * has been inserted, returns lock.
 * </li>
 * <li>
 * We will try to update lock record using filter _id == name AND lock_until &lt;= now
 * </li>
 * <li>
 * If the update succeeded (1 updated document), we have the lock. If the update failed (0 updated documents) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class OpenSearchLockProvider implements LockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String NAME = "name";


    private static final String UPDATE_SCRIPT =
        "if (ctx._source." + LOCK_UNTIL + " <= " + "params." + LOCKED_AT + ") { " +
            "ctx._source." + LOCKED_BY + " = params." + LOCKED_BY + "; " +
            "ctx._source." + LOCKED_AT + " = params." + LOCKED_AT + "; " +
            "ctx._source." + LOCK_UNTIL + " =  params." + LOCK_UNTIL + "; " +
            "} else { " +
            "ctx.op = 'none' " +
            "}";

    private final RestHighLevelClient highLevelClient;
    private final String hostname;
    private final String index;

    private OpenSearchLockProvider(@NonNull RestHighLevelClient highLevelClient, @NonNull String index) {
        this.highLevelClient = highLevelClient;
        this.hostname = getHostname();
        this.index = index;
    }

    public OpenSearchLockProvider(@NonNull RestHighLevelClient highLevelClient) {
        this(highLevelClient, SCHEDLOCK_DEFAULT_INDEX);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        try {
            Map<String, Object> lockObject = lockObject(lockConfiguration.getName(),
                lockConfiguration.getLockAtMostUntil(),
                now());
            UpdateRequest ur = updateRequest(lockConfiguration)
                .script(new Script(ScriptType.INLINE,
                    "painless",
                    UPDATE_SCRIPT,
                    lockObject)
                )
                .upsert(lockObject);
            UpdateResponse res = highLevelClient.update(ur, RequestOptions.DEFAULT);
            if (res.getResult() != DocWriteResponse.Result.NOOP) {
                return Optional.of(new OpenSearchSimpleLock(lockConfiguration));
            } else {
                return Optional.empty();
            }
        } catch (IOException | OpenSearchException e) {
            if (e instanceof OpenSearchException && ((OpenSearchException) e).status() == RestStatus.CONFLICT) {
                return Optional.empty();
            } else {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }

    private UpdateRequest updateRequest(@NonNull LockConfiguration lockConfiguration) {
        return new UpdateRequest()
            .index(index)
            .id(lockConfiguration.getName())
            .setRefreshPolicy(IMMEDIATE);
    }

    private Map<String, Object> lockObject(String name, Instant lockUntil, Instant lockedAt) {
        Map<String, Object> lock = new HashMap<>();
        lock.put(NAME, name);
        lock.put(LOCKED_BY, hostname);
        lock.put(LOCKED_AT, lockedAt.toEpochMilli());
        lock.put(LOCK_UNTIL, lockUntil.toEpochMilli());
        return lock;
    }

    private final class OpenSearchSimpleLock extends AbstractSimpleLock {

        private OpenSearchSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            // Set lockUtil to now or lockAtLeastUntil whichever is later
            try {
                UpdateRequest ur = updateRequest(lockConfiguration)
                    .script(new Script(ScriptType.INLINE,
                        "painless",
                        "ctx._source.lockUntil = params.unlockTime",
                        Collections.singletonMap("unlockTime", lockConfiguration.getUnlockTime().toEpochMilli())));
                highLevelClient.update(ur, RequestOptions.DEFAULT);
            } catch (IOException | OpenSearchException e) {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }
}
