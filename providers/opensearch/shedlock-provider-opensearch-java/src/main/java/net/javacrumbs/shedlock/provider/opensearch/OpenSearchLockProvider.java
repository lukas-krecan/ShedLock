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
package net.javacrumbs.shedlock.provider.opensearch;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateResponse;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;

/**
 * Lock using OpenSearch &gt;= . Requires opensearch-rest-high-level-client &gt;
 * 1.1.0
 *
 * <p>
 * It uses a collection that contains documents like this:
 *
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
 *
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the
 * code
 *
 * <ol>
 * <li>Attempts to insert a new lock record. As an optimization, we keep
 * in-memory track of created lock records. If the record has been inserted,
 * returns lock.
 * <li>We will try to update lock record using filter _id == name AND lock_until
 * &lt;= now
 * <li>If the update succeeded (1 updated document), we have the lock. If the
 * update failed (0 updated documents) somebody else holds the lock
 * <li>When unlocking, lock_until is set to now.
 * </ol>
 */
public class OpenSearchLockProvider implements LockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String NAME = "name";

    private static final String UPDATE_SCRIPT = "if (ctx._source." + LOCK_UNTIL + " <= " + "params." + LOCKED_AT
        + ") { " + "ctx._source." + LOCKED_BY + " = params." + LOCKED_BY + "; " + "ctx._source." + LOCKED_AT
        + " = params." + LOCKED_AT + "; " + "ctx._source." + LOCK_UNTIL + " =  params." + LOCK_UNTIL + "; "
        + "} else { " + "ctx.op = 'none' " + "}";

    private final OpenSearchClient client;
    private final String hostname;
    private final String index;

    private OpenSearchLockProvider(@NonNull OpenSearchClient client, @NonNull String index) {
        this.client = client;
        this.hostname = getHostname();
        this.index = index;
    }

    public OpenSearchLockProvider(@NonNull OpenSearchClient client) {
        this(client, SCHEDLOCK_DEFAULT_INDEX);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        try {
            Map<String, JsonData> lockObject = lockObject(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now());
            UpdateRequest<Map<String, JsonData>, Map<String, JsonData>> ur = updateRequest(lockConfiguration)
                .script(script(UPDATE_SCRIPT, lockObject))
                .upsert(lockObject).build();
            UpdateResponse<Map<String, JsonData>> res = client.update(ur, (Class<Map<String, JsonData>>) lockObject.getClass());
            if (res.result() != Result.NoOp) {
                return Optional.of(new OpenSearchSimpleLock(lockConfiguration));
            } else {
                return Optional.empty();
            }
        } catch (IOException | OpenSearchException e) {
            if (e instanceof OpenSearchException && ((OpenSearchException) e).status() == 409) {
                return Optional.empty();
            } else {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }

    private static Script script(String script, Map<String, JsonData> params) {
        InlineScript inlineScript = new InlineScript.Builder().source(script).lang("painless").params(params).build();
        return new Script.Builder().inline(inlineScript).build();
    }

    private UpdateRequest.Builder<Map<String, JsonData>, Map<String, JsonData>> updateRequest(@NonNull LockConfiguration lockConfiguration) {
        return new UpdateRequest.Builder<Map<String, JsonData>, Map<String, JsonData>>().index(index).id(lockConfiguration.getName());
    }

    private Map<String, JsonData> lockObject(String name, Instant lockUntil, Instant lockedAt) {
        return Map.of(
            NAME,
            JsonData.of(name),
            LOCKED_BY,
            JsonData.of(hostname),
            LOCKED_AT,
            JsonData.of(lockedAt.toEpochMilli()),
            LOCK_UNTIL,
            JsonData.of(lockUntil.toEpochMilli()));
    }

    private final class OpenSearchSimpleLock extends AbstractSimpleLock {

        private OpenSearchSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            // Set lockUtil to now or lockAtLeastUntil whichever is later
            try {
                UpdateRequest<Map<String, JsonData>, Map<String, JsonData>> ur = updateRequest(lockConfiguration)
                    .script(script("ctx._source.lockUntil = params.unlockTime", Map.of(
                        "unlockTime",
                        JsonData.of(lockConfiguration.getUnlockTime().toEpochMilli()))))
                    .upsert(lockObject).build();
                UpdateRequest ur = updateRequest(lockConfiguration)
                    .script(new Script.Builder().inline(
                        ScriptType.INLINE,
                        "painless",
                        "ctx._source.lockUntil = params.unlockTime",
                        );
                client.update(ur, RequestOptions.DEFAULT);
            } catch (IOException | OpenSearchException e) {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }
}
