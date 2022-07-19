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
package net.javacrumbs.shedlock.provider.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.UpdateRequest;
import co.elastic.clients.elasticsearch.core.UpdateResponse;
import co.elastic.clients.json.JsonData;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.provider.elasticsearch.model.LockPOJO;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.elasticsearch.client.ResponseException;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.support.Utils.getHostname;

/**
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
public class Elasticsearch8LockProvider implements LockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";
    static final String SCHEDLOCK_DEFAULT_TYPE = "lock";
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

    private final ElasticsearchClient client;
    private final String hostname;
    private final String index;
    private final String type;

    private Elasticsearch8LockProvider(@NonNull ElasticsearchClient client, @NonNull String index, @NonNull String type) {
        this.client = client;
        this.hostname = getHostname();
        this.index = index;
        this.type = type;
    }

    public Elasticsearch8LockProvider(@NonNull ElasticsearchClient client, @NonNull String documentType) {
        this(client, SCHEDLOCK_DEFAULT_INDEX, documentType);
    }

    public Elasticsearch8LockProvider(@NonNull ElasticsearchClient client) {
        this(client, SCHEDLOCK_DEFAULT_INDEX, SCHEDLOCK_DEFAULT_TYPE);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        try {
            Instant now = now();
            Instant lockAtMostUntil = lockConfiguration.getLockAtMostUntil();
            Map<String, JsonData> lockObject =
                lockObject(lockConfiguration.getName(), lockAtMostUntil, now);

            LockPOJO pojo = new LockPOJO(lockConfiguration.getName(), hostname, now.toEpochMilli(), lockAtMostUntil.toEpochMilli());

            UpdateRequest<LockPOJO, LockPOJO> updateRequest = UpdateRequest.of(ur -> ur
                .index(index)
                .id(lockConfiguration.getName())
                .refresh(Refresh.True)
                .script(sc -> sc.inline(in -> in.lang("painless").source(UPDATE_SCRIPT).params(lockObject)))
                .upsert(pojo));

            UpdateResponse<LockPOJO> res = client.update(updateRequest, LockPOJO.class);
            if (res.result() != Result.NoOp) {
                return Optional.of(new ElasticsearchSimpleLock(lockConfiguration));
            } else { //nothing happened
                return Optional.empty();
            }
        } catch (IOException | ElasticsearchException e) {
            if ((e instanceof ElasticsearchException && ((ElasticsearchException)e).status() == 409) ||
                (e instanceof ResponseException && ((ResponseException)e).getResponse().getStatusLine().getStatusCode() == 409)) {
                return Optional.empty();
            } else {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }

    private Map<String, JsonData> lockObject(String name, Instant lockUntil, Instant lockedAt) {
        Map<String, JsonData> lock = new HashMap<>();
        lock.put(NAME, JsonData.of(name));
        lock.put(LOCKED_BY, JsonData.of(hostname));
        lock.put(LOCKED_AT, JsonData.of(lockedAt.toEpochMilli()));
        lock.put(LOCK_UNTIL, JsonData.of(lockUntil.toEpochMilli()));
        return lock;
    }

    private final class ElasticsearchSimpleLock extends AbstractSimpleLock {

        private ElasticsearchSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            // Set lockUtil to now or lockAtLeastUntil whichever is later
            try {
                Map<String, JsonData> lockObject = Collections.singletonMap("unlockTime", JsonData.of(lockConfiguration.getUnlockTime().toEpochMilli()));

                UpdateRequest<LockPOJO, LockPOJO> updateRequest = UpdateRequest.of(ur -> ur
                        .index(index)
                        .id(lockConfiguration.getName())
                        .refresh(Refresh.True)
                        .script(sc -> sc.inline(in -> in.lang("painless").source("ctx._source.lockUntil = params.unlockTime")
                            .params(lockObject))));

                UpdateResponse<LockPOJO> res = client.update(updateRequest, LockPOJO.class);
                if (res.result() == Result.NoOp) { //nothing happened
                    //no unlock operation was done
                }
            } catch (IOException | ElasticsearchException e) {
                throw new LockException("Unexpected exception occurred", e);
            }
        }
    }
}
