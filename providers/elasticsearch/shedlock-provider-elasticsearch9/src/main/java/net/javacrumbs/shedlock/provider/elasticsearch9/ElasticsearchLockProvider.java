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
 *     ElasticsearchLockProvider.Configuration.builder(client)
 *         .withFieldNames(DocumentFieldNames.SNAKE_CASE)
 *         .build()
 * );
 * </pre>
 */
public class ElasticsearchLockProvider implements LockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";

    // Script parameter keys
    private static final String PARAM_LOCK_UNTIL_FIELD = "lockUntilField";
    private static final String PARAM_LOCKED_AT_FIELD = "lockedAtField";
    private static final String PARAM_LOCKED_BY_FIELD = "lockedByField";
    private static final String PARAM_NOW = "now";
    private static final String PARAM_LOCK_UNTIL = "lockUntil";
    private static final String PARAM_LOCKED_BY = "lockedBy";
    private static final String PARAM_UNLOCK_TIME = "unlockTime";

    /**
     * Lock script uses bracket notation for field access to support any valid ES field names.
     * Field names are passed as params for script caching.
     *
     * <p>Fail-fast behavior: If the lockUntil field is missing or not a Number, the script
     * throws an exception to make configuration errors visible. This typically indicates
     * a field name mismatch (e.g., using SNAKE_CASE config on data with camelCase fields)
     * without proper data migration.
     */
    private static final String LOCK_SCRIPT =
            """
            def v = ctx._source[params.lockUntilField];
            if (!(v instanceof Number)) {
                throw new IllegalStateException("Field '" + params.lockUntilField + "' is missing or not a Number. " +
                    "Possible field name mismatch - check DocumentFieldNames configuration and ensure data migration was performed.");
            }
            if (((Number) v).longValue() <= params.now) {
                ctx._source[params.lockUntilField] = params.lockUntil;
                ctx._source[params.lockedAtField] = params.now;
                ctx._source[params.lockedByField] = params.lockedBy;
            } else {
                ctx.op = 'none';
            }""";

    /**
     * Unlock script uses same bracket notation pattern as lock script for consistency.
     */
    private static final String UNLOCK_SCRIPT = "ctx._source[params.lockUntilField] = params.unlockTime;";

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
        this(Configuration.builder(client).build());
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        try {
            Instant now = now();
            Instant lockAtMostUntil = lockConfiguration.getLockAtMostUntil();
            Map<String, JsonData> lockParams = createLockParams(lockAtMostUntil, now);
            Map<String, Object> upsertDoc = createUpsertDocument(lockConfiguration.getName(), lockAtMostUntil, now);

            UpdateRequest<Map<String, Object>, Map<String, Object>> updateRequest =
                    UpdateRequest.of(ur -> ur.index(index)
                            .id(lockConfiguration.getName())
                            .refresh(Refresh.True)
                            .script(sc -> sc.lang("painless")
                                    .source(builder -> builder.scriptString(LOCK_SCRIPT))
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
                throw new LockException("Unexpected exception while locking", e);
            }
        }
    }

    private Map<String, JsonData> createLockParams(Instant lockUntil, Instant lockedAt) {
        return Map.of(
                PARAM_LOCK_UNTIL_FIELD,
                JsonData.of(fieldNames.lockUntil()),
                PARAM_LOCKED_AT_FIELD,
                JsonData.of(fieldNames.lockedAt()),
                PARAM_LOCKED_BY_FIELD,
                JsonData.of(fieldNames.lockedBy()),
                PARAM_NOW,
                JsonData.of(lockedAt.toEpochMilli()),
                PARAM_LOCK_UNTIL,
                JsonData.of(lockUntil.toEpochMilli()),
                PARAM_LOCKED_BY,
                JsonData.of(hostname));
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
                        PARAM_LOCK_UNTIL_FIELD,
                        JsonData.of(fieldNames.lockUntil()),
                        PARAM_UNLOCK_TIME,
                        JsonData.of(lockConfiguration.getUnlockTime().toEpochMilli()));

                UpdateRequest<Map<String, Object>, Map<String, Object>> updateRequest =
                        UpdateRequest.of(ur -> ur.index(index)
                                .id(lockConfiguration.getName())
                                .refresh(Refresh.True)
                                .script(sc -> sc.lang("painless")
                                        .source(builder -> builder.scriptString(UNLOCK_SCRIPT))
                                        .params(unlockParams)));
                client.update(updateRequest, Map.class);
            } catch (IOException | ElasticsearchException e) {
                throw new LockException("Unexpected exception while unlocking", e);
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

        public static Builder builder(ElasticsearchClient client) {
            return new Builder(client);
        }

        /**
         * Builder for ElasticsearchLockProvider.Configuration.
         */
        public static final class Builder {
            private final ElasticsearchClient client;
            private String index = SCHEDLOCK_DEFAULT_INDEX;
            private DocumentFieldNames fieldNames = DocumentFieldNames.DEFAULT;

            private Builder(ElasticsearchClient client) {
                this.client = requireNonNull(client, "client cannot be null");
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
             */
            public Configuration build() {
                return new Configuration(client, index, fieldNames);
            }
        }
    }
}
