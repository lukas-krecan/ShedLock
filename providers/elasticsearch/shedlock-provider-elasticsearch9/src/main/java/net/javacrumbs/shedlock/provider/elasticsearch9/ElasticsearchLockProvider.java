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
package net.javacrumbs.shedlock.provider.elasticsearch9;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;

/**
 * Elasticsearch-based lock provider.
 *
 * <p>It uses a collection that contains documents like this:
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
 * ElasticsearchLockProvider provider = new ElasticsearchLockProvider(
 *     ElasticsearchLockProvider.Configuration.builder()
 *         .withClient(client)
 *         .withFieldNames(DocumentFieldNames.SNAKE_CASE)
 *         .build()
 * );
 * </pre>
 */
public class ElasticsearchLockProvider implements LockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";
    private static final String UNLOCK_TIME = "unlockTime";

    private final ElasticsearchClient client;
    private final String hostname;
    private final String index;
    private final DocumentFieldNames fieldNames;

    /**
     * Creates a new ElasticsearchLockProvider with the specified configuration.
     *
     * @param configuration the configuration containing client, index, and field names
     */
    public ElasticsearchLockProvider(Configuration configuration) {
        this.client = requireNonNull(configuration.getClient(), "client cannot be null");
        this.index = requireNonNull(configuration.getIndex(), "index cannot be null");
        this.fieldNames = requireNonNull(configuration.getFieldNames(), "fieldNames cannot be null");
        this.hostname = getHostname();
    }

    /**
     * Creates a new ElasticsearchLockProvider with default configuration.
     *
     * @param client the Elasticsearch client
     */
    public ElasticsearchLockProvider(ElasticsearchClient client) {
        this(Configuration.builder().withClient(client).build());
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        try {
            Instant now = now();
            Instant lockAtMostUntil = lockConfiguration.getLockAtMostUntil();
            Map<String, JsonData> lockParams = createLockParams(lockConfiguration.getName(), lockAtMostUntil, now);
            Map<String, Object> upsertDoc = createUpsertDocument(lockConfiguration.getName(), lockAtMostUntil, now);

            UpdateRequest<Map<String, Object>, Map<String, Object>> updateRequest =
                    UpdateRequest.of(ur -> ur.index(index)
                            .id(lockConfiguration.getName())
                            .refresh(Refresh.True)
                            .script(sc -> sc.lang("painless")
                                    .source(builder -> builder.scriptString(buildUpdateScript()))
                                    .params(lockParams))
                            .upsert(upsertDoc));

            UpdateResponse<Map<String, Object>> res = client.update(updateRequest, Map.class);
            if (res.result() != Result.NoOp) {
                return Optional.of(new ElasticsearchSimpleLock(lockConfiguration));
            } else {
                return Optional.empty();
            }
        } catch (IOException | ElasticsearchException e) {
            if ((e instanceof ElasticsearchException ex && ex.status() == 409)) {
                return Optional.empty();
            } else {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
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

    private final class ElasticsearchSimpleLock extends AbstractSimpleLock {

        private ElasticsearchSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            try {
                Map<String, JsonData> unlockParams = Map.of(
                        UNLOCK_TIME,
                        JsonData.of(lockConfiguration.getUnlockTime().toEpochMilli()));

                String unlockScript = "ctx._source." + fieldNames.lockUntil() + " = params." + UNLOCK_TIME;

                UpdateRequest<Map<String, Object>, Map<String, Object>> updateRequest =
                        UpdateRequest.of(ur -> ur.index(index)
                                .id(lockConfiguration.getName())
                                .refresh(Refresh.True)
                                .script(sc -> sc.lang("painless")
                                        .source(builder -> builder.scriptString(unlockScript))
                                        .params(unlockParams)));
                client.update(updateRequest, Map.class);
            } catch (IOException | ElasticsearchException e) {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }

    /**
     * Configuration for ElasticsearchLockProvider.
     */
    public static final class Configuration {
        private final ElasticsearchClient client;
        private final String index;
        private final DocumentFieldNames fieldNames;

        Configuration(ElasticsearchClient client, String index, DocumentFieldNames fieldNames) {
            this.client = requireNonNull(client, "client cannot be null");
            this.index = requireNonNull(index, "index cannot be null");
            this.fieldNames = requireNonNull(fieldNames, "fieldNames cannot be null");
        }

        public ElasticsearchClient getClient() {
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
         * Builder for ElasticsearchLockProvider.Configuration.
         */
        public static final class Builder {
            private ElasticsearchClient client;
            private String index = SCHEDLOCK_DEFAULT_INDEX;
            private DocumentFieldNames fieldNames = DocumentFieldNames.DEFAULT;

            /**
             * Sets the Elasticsearch client (required).
             *
             * @param client the Elasticsearch client
             * @return this builder
             */
            public Builder withClient(ElasticsearchClient client) {
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
             * <p>Use {@link DocumentFieldNames#SNAKE_CASE} when your ElasticsearchClient
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
