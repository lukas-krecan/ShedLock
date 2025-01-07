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

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static org.opensearch.action.support.WriteRequest.RefreshPolicy.IMMEDIATE;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.apache.http.HttpStatus;
import org.opensearch.OpenSearchException;
import org.opensearch.action.DocWriteResponse;
import org.opensearch.action.update.UpdateRequest;
import org.opensearch.action.update.UpdateResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.transport.httpclient5.ResponseException;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptType;

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
    private static final String UNLOCK_UPDATE_SCRIPT = "ctx._source.lockUntil = params.unlockTime";
    private static final String PAINLESS_SCRIPT_LANG = "painless";
    private static final String UNLOCK_TIME = "unlockTime";

    private final RestHighLevelClient highLevelClient;
    private final OpenSearchClient openSearchClient;
    private final String hostname;
    private final String index;

    /**
     *
     * @param highLevelClient rest high level client
     * @deprecated {@link RestHighLevelClient} is deprecated. Use other constructor with {@link OpenSearchClient}.
     */
    @Deprecated(forRemoval = true)
    public OpenSearchLockProvider(@NonNull RestHighLevelClient highLevelClient) {
        this.highLevelClient = highLevelClient;
        this.index = SCHEDLOCK_DEFAULT_INDEX;
        this.openSearchClient = null;
        this.hostname = getHostname();
    }

    public OpenSearchLockProvider(@NonNull OpenSearchClient openSearchClient) {
        this.openSearchClient = openSearchClient;
        this.index = SCHEDLOCK_DEFAULT_INDEX;
        this.hostname = getHostname();
        this.highLevelClient = null;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        return Optional.ofNullable(highLevelClient)
                .map(client -> lockUsingRestHighLevelClient(lockConfiguration))
                .orElseGet(() -> lockUsingOpenSearchClient(lockConfiguration));
    }

    private Optional<SimpleLock> lockUsingRestHighLevelClient(LockConfiguration lockConfiguration) {
        try {
            Map<String, Object> lockObject =
                    lockObject(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now());
            UpdateRequest ur = updateRequest(lockConfiguration)
                    .script(new Script(ScriptType.INLINE, PAINLESS_SCRIPT_LANG, UPDATE_SCRIPT, lockObject))
                    .upsert(lockObject);
            UpdateResponse res = highLevelClient.update(ur, RequestOptions.DEFAULT);
            if (res.getResult() != DocWriteResponse.Result.NOOP) {
                return Optional.of(new OpenSearchSimpleLock(lockConfiguration));
            } else {
                return Optional.empty();
            }
        } catch (IOException | OpenSearchException e) {
            if (isOpenSearchExceptionWithConflictStatus(e)) {
                return Optional.empty();
            } else {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }

    private Optional<SimpleLock> lockUsingOpenSearchClient(LockConfiguration lockConfiguration) {
        Instant now = now();
        org.opensearch.client.opensearch.core.UpdateRequest<Object, Object> updateRequest =
                createUpdateRequest(lockConfiguration, now);

        try {
            org.opensearch.client.opensearch.core.UpdateResponse<Object> updateResponse =
                    openSearchClient.update(updateRequest, Object.class);

            return updateResponse.result() == Result.NoOp
                    ? Optional.empty()
                    : Optional.of(new OpenSearchSimpleLock(lockConfiguration));
        } catch (IOException | OpenSearchException e) {
            if (isResponseExceptionWithConflictStatus(e) || isOpenSearchExceptionWithConflictStatus(e)) {
                return Optional.empty();
            }

            throw new LockException("Unexpected exception occurred", e);
        }
    }

    private static boolean isResponseExceptionWithConflictStatus(Exception e) {
        return e instanceof ResponseException && ((ResponseException) e).status() == HttpStatus.SC_CONFLICT;
    }

    private static boolean isOpenSearchExceptionWithConflictStatus(Exception e) {
        return e instanceof OpenSearchException && ((OpenSearchException) e).status() == RestStatus.CONFLICT;
    }

    private org.opensearch.client.opensearch.core.UpdateRequest<Object, Object> createUpdateRequest(
            LockConfiguration lockConfiguration, Instant now) {

        Map<String, Object> lockObject =
                lockObject(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now);

        return new org.opensearch.client.opensearch.core.UpdateRequest.Builder<>()
                .index(index)
                .script(createUpdateScript(lockConfiguration, now))
                .id(lockConfiguration.getName())
                .refresh(Refresh.True)
                .upsert(lockObject)
                .build();
    }

    private org.opensearch.client.opensearch._types.Script createUpdateScript(
            LockConfiguration lockConfiguration, Instant now) {
        Map<String, JsonData> updateScriptParams =
                updateScriptParams(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now);

        InlineScript inlineScript = InlineScript.of(builder ->
                builder.source(UPDATE_SCRIPT).params(updateScriptParams).lang(PAINLESS_SCRIPT_LANG));

        return org.opensearch.client.opensearch._types.Script.of(scriptBuilder -> scriptBuilder.inline(inlineScript));
    }

    private UpdateRequest updateRequest(@NonNull LockConfiguration lockConfiguration) {
        return new UpdateRequest().index(index).id(lockConfiguration.getName()).setRefreshPolicy(IMMEDIATE);
    }

    private Map<String, Object> lockObject(String name, Instant lockUntil, Instant lockedAt) {
        return Map.of(
                NAME,
                name,
                LOCKED_BY,
                hostname,
                LOCKED_AT,
                lockedAt.toEpochMilli(),
                LOCK_UNTIL,
                lockUntil.toEpochMilli());
    }

    private Map<String, JsonData> updateScriptParams(String name, Instant lockUntil, Instant lockedAt) {
        return Map.of(
                NAME, JsonData.of(name),
                LOCKED_BY, JsonData.of(hostname),
                LOCKED_AT, JsonData.of(lockedAt.toEpochMilli()),
                LOCK_UNTIL, JsonData.of(lockUntil.toEpochMilli()));
    }

    private final class OpenSearchSimpleLock extends AbstractSimpleLock {

        private OpenSearchSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            // Set lockUtil to now or lockAtLeastUntil whichever is later
            Optional.ofNullable(highLevelClient)
                    .ifPresentOrElse(restClient -> unlockUsingRestClient(), this::unlockUsingOpenSearchClient);
        }

        private void unlockUsingRestClient() {
            UpdateRequest updateRequest = updateRequest(lockConfiguration)
                    .script(new Script(
                            ScriptType.INLINE,
                            PAINLESS_SCRIPT_LANG,
                            "ctx._source.lockUntil = params.unlockTime",
                            Map.of(
                                    "unlockTime",
                                    lockConfiguration.getUnlockTime().toEpochMilli())));

            Optional.ofNullable(highLevelClient).ifPresent(client -> {
                try {
                    highLevelClient.update(updateRequest, RequestOptions.DEFAULT);
                } catch (IOException | OpenSearchException e) {
                    throwLockException(e);
                }
            });
        }

        private void unlockUsingOpenSearchClient() {
            Map<String, JsonData> unlockParams = Map.of(
                    UNLOCK_TIME, JsonData.of(lockConfiguration.getUnlockTime().toEpochMilli()));

            InlineScript inlineScript = InlineScript.of(builder ->
                    builder.source(UNLOCK_UPDATE_SCRIPT).params(unlockParams).lang(PAINLESS_SCRIPT_LANG));

            org.opensearch.client.opensearch._types.Script unlockScript =
                    org.opensearch.client.opensearch._types.Script.of(
                            scriptBuilder -> scriptBuilder.inline(inlineScript));

            org.opensearch.client.opensearch.core.UpdateRequest<Object, Object> unlockUpdateRequest =
                    new org.opensearch.client.opensearch.core.UpdateRequest.Builder<>()
                            .index(index)
                            .script(unlockScript)
                            .id(lockConfiguration.getName())
                            .refresh(Refresh.True)
                            .build();

            Optional.ofNullable(openSearchClient).ifPresent(client -> {
                try {
                    client.update(unlockUpdateRequest, Object.class);
                } catch (IOException | OpenSearchException e) {
                    throwLockException(e);
                }
            });
        }

        private void throwLockException(Exception e) {
            throw new LockException("Unexpected exception occurred", e);
        }
    }
}
