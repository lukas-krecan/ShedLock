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
package net.javacrumbs.shedlock.provider.opensearch.java;

import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.BuiltinScriptLanguage;
import org.opensearch.client.opensearch._types.InlineScript;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch._types.Refresh;
import org.opensearch.client.opensearch._types.Result;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch.core.UpdateRequest;
import org.opensearch.client.opensearch.core.UpdateRequest.Builder;
import org.opensearch.client.opensearch.core.UpdateResponse;
import org.opensearch.client.transport.httpclient5.ResponseException;

/**
 * OpenSearch-based lock provider.
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
 *
 * <p>Example with custom field names for SNAKE_CASE JsonpMapper:
 * <pre>
 * OpenSearchLockProvider provider = new OpenSearchLockProvider(
 *     OpenSearchLockProvider.Configuration.builder()
 *         .withClient(client)
 *         .withFieldNames(DocumentFieldNames.SNAKE_CASE)
 *         .build()
 * );
 * </pre>
 */
public class OpenSearchLockProvider implements LockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";
    private static final String UNLOCK_TIME = "unlockTime";

    private final OpenSearchClient openSearchClient;
    private final String hostname;
    private final String index;
    private final DocumentFieldNames fieldNames;

    /**
     * Creates a new OpenSearchLockProvider with the specified configuration.
     *
     * @param configuration the configuration containing client, index, and field names
     */
    public OpenSearchLockProvider(Configuration configuration) {
        this.openSearchClient = requireNonNull(configuration.getClient(), "client cannot be null");
        this.index = requireNonNull(configuration.getIndex(), "index cannot be null");
        this.fieldNames = requireNonNull(configuration.getFieldNames(), "fieldNames cannot be null");
        this.hostname = getHostname();
    }

    /**
     * Creates a new OpenSearchLockProvider with default configuration.
     *
     * @param openSearchClient the OpenSearch client
     */
    public OpenSearchLockProvider(OpenSearchClient openSearchClient) {
        this(openSearchClient, SCHEDLOCK_DEFAULT_INDEX);
    }

    /**
     * Creates a new OpenSearchLockProvider with a custom index name.
     *
     * @param openSearchClient the OpenSearch client
     * @param index the index name
     */
    public OpenSearchLockProvider(OpenSearchClient openSearchClient, String index) {
        this(Configuration.builder()
                .withClient(openSearchClient)
                .withIndex(index)
                .build());
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Instant now = now();
        UpdateRequest<Object, Object> updateRequest = createUpdateRequest(lockConfiguration, now);

        try {
            UpdateResponse<Object> updateResponse = openSearchClient.update(updateRequest, Object.class);

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
        return e instanceof ResponseException ex && ex.status() == HTTP_CONFLICT;
    }

    private static boolean isOpenSearchExceptionWithConflictStatus(Exception e) {
        return e instanceof OpenSearchException ex && ex.status() == HTTP_CONFLICT;
    }

    private UpdateRequest<Object, Object> createUpdateRequest(LockConfiguration lockConfiguration, Instant now) {
        Map<String, Object> upsertDoc =
                createUpsertDocument(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now);

        return new Builder<>()
                .index(index)
                .script(createUpdateScript(lockConfiguration, now))
                .id(lockConfiguration.getName())
                .refresh(Refresh.True)
                .upsert(upsertDoc)
                .build();
    }

    private Script createUpdateScript(LockConfiguration lockConfiguration, Instant now) {
        Map<String, JsonData> updateScriptParams =
                createLockParams(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now);

        InlineScript inlineScript = inlineScript(buildUpdateScript(), updateScriptParams);

        return Script.of(scriptBuilder -> scriptBuilder.inline(inlineScript));
    }

    private String buildUpdateScript() {
        return """
                if (ctx._source.%s <= params.%s) {
                    ctx._source.%s = params.%s;
                    ctx._source.%s = params.%s;
                    ctx._source.%s = params.%s;
                } else {
                    ctx.op = 'none'
                }"""
                .formatted(
                        fieldNames.lockUntil(),
                        fieldNames.lockedAt(),
                        fieldNames.lockedBy(),
                        fieldNames.lockedBy(),
                        fieldNames.lockedAt(),
                        fieldNames.lockedAt(),
                        fieldNames.lockUntil(),
                        fieldNames.lockUntil());
    }

    private Map<String, Object> createUpsertDocument(String name, Instant lockUntil, Instant lockedAt) {
        return Map.of(
                fieldNames.name(),
                name,
                fieldNames.lockedBy(),
                hostname,
                fieldNames.lockedAt(),
                lockedAt.toEpochMilli(),
                fieldNames.lockUntil(),
                lockUntil.toEpochMilli());
    }

    private Map<String, JsonData> createLockParams(String name, Instant lockUntil, Instant lockedAt) {
        return Map.of(
                fieldNames.name(),
                JsonData.of(name),
                fieldNames.lockedBy(),
                JsonData.of(hostname),
                fieldNames.lockedAt(),
                JsonData.of(lockedAt.toEpochMilli()),
                fieldNames.lockUntil(),
                JsonData.of(lockUntil.toEpochMilli()));
    }

    private static InlineScript inlineScript(String sc, Map<String, JsonData> params) {
        return InlineScript.of(
                builder -> builder.source(sc).params(params).lang(l -> l.builtin(BuiltinScriptLanguage.Painless)));
    }

    private final class OpenSearchSimpleLock extends AbstractSimpleLock {

        private OpenSearchSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            Map<String, JsonData> unlockParams = Map.of(
                    UNLOCK_TIME, JsonData.of(lockConfiguration.getUnlockTime().toEpochMilli()));

            String unlockScript = "ctx._source." + fieldNames.lockUntil() + " = params." + UNLOCK_TIME;
            InlineScript inlineScript = inlineScript(unlockScript, unlockParams);

            Script script = Script.of(scriptBuilder -> scriptBuilder.inline(inlineScript));

            UpdateRequest<Object, Object> unlockUpdateRequest =
                    new org.opensearch.client.opensearch.core.UpdateRequest.Builder<>()
                            .index(index)
                            .script(script)
                            .id(lockConfiguration.getName())
                            .refresh(Refresh.True)
                            .build();

            try {
                openSearchClient.update(unlockUpdateRequest, Object.class);
            } catch (IOException | OpenSearchException e) {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }

    /**
     * Configuration for OpenSearchLockProvider.
     */
    public static final class Configuration {
        private final OpenSearchClient client;
        private final String index;
        private final DocumentFieldNames fieldNames;

        Configuration(OpenSearchClient client, String index, DocumentFieldNames fieldNames) {
            this.client = requireNonNull(client, "client cannot be null");
            this.index = requireNonNull(index, "index cannot be null");
            this.fieldNames = requireNonNull(fieldNames, "fieldNames cannot be null");
        }

        public OpenSearchClient getClient() {
            return client;
        }

        public String getIndex() {
            return index;
        }

        public DocumentFieldNames getFieldNames() {
            return fieldNames;
        }

        public static Builder builder() {
            return new Builder();
        }

        /**
         * Builder for OpenSearchLockProvider.Configuration.
         */
        public static final class Builder {
            private OpenSearchClient client;
            private String index = SCHEDLOCK_DEFAULT_INDEX;
            private DocumentFieldNames fieldNames = DocumentFieldNames.DEFAULT;

            /**
             * Sets the OpenSearch client (required).
             *
             * @param client the OpenSearch client
             * @return this builder
             */
            public Builder withClient(OpenSearchClient client) {
                this.client = client;
                return this;
            }

            /**
             * Sets the index name. Defaults to "shedlock".
             *
             * @param index the index name
             * @return this builder
             */
            public Builder withIndex(String index) {
                this.index = index;
                return this;
            }

            /**
             * Sets the document field names. Defaults to {@link DocumentFieldNames#DEFAULT}.
             *
             * <p>Use {@link DocumentFieldNames#SNAKE_CASE} when your OpenSearchClient
             * is configured with a JsonpMapper using SNAKE_CASE naming strategy.
             *
             * @param fieldNames the field names configuration
             * @return this builder
             */
            public Builder withFieldNames(DocumentFieldNames fieldNames) {
                this.fieldNames = fieldNames;
                return this;
            }

            /**
             * Builds the Configuration.
             *
             * @return the configuration
             * @throws NullPointerException if client is not set
             */
            public Configuration build() {
                return new Configuration(requireNonNull(client, "client is required"), index, fieldNames);
            }
        }
    }
}
