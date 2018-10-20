package net.javacrumbs.shedlock.provider.elasticsearch;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.StorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.WriteRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptType;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Lock using ElasticSearch &gt;= 6.4.0.
 * Requires elasticsearch-rest-high-level-client &gt; 6.4.0
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

public class ElasticsearchLockProvider extends StorageBasedLockProvider {
    static final String SCHEDLOCK_DEFAULT_INDEX = "shedlock";
    static final String SCHEDLOCK_DEFAULT_TYPE = "lock";
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String NAME = "name";

    private ElasticsearchLockProvider(StorageAccessor storageAccessor) {
        super(storageAccessor);
    }

    public ElasticsearchLockProvider(RestHighLevelClient highLevelClient, String shedLockIndex, String documentType) {
        this(new ElasticAccessor(highLevelClient, shedLockIndex, documentType));
    }

    public ElasticsearchLockProvider(RestHighLevelClient highLevelClient, String documentType) {
        this(highLevelClient, SCHEDLOCK_DEFAULT_INDEX, documentType);
    }

    public ElasticsearchLockProvider(RestHighLevelClient highLevelClient) {
        this(highLevelClient, SCHEDLOCK_DEFAULT_INDEX, SCHEDLOCK_DEFAULT_TYPE);
    }

    static class ElasticAccessor extends AbstractStorageAccessor {

        public static final String UPDATE_SCRIPT = "if (ctx._source." + LOCK_UNTIL + " <= " + "params." + LOCKED_AT + ") { " +
                "ctx._source." + LOCKED_BY + " = params." + LOCKED_BY + "; " +
                "ctx._source." + LOCKED_AT + " = params." + LOCKED_AT + "; " +
                "ctx._source." + LOCK_UNTIL + " =  params." + LOCK_UNTIL + "; " +
                "} else { " +
                "ctx.op = 'none' " +
                "}";
        private final RestHighLevelClient highLevelClient;
        private final String hostname;
        private final String index;
        private final String type;

        ElasticAccessor(RestHighLevelClient highLevelClient, String index, String type) {
            this.highLevelClient = highLevelClient;
            this.hostname = getHostname();
            this.index = index;
            this.type = type;
        }

        @Override
        public boolean insertRecord(LockConfiguration lockConfiguration) {
            try {
                IndexRequest ir = new IndexRequest()
                        .index(index)
                        .type(type)
                        .id(lockConfiguration.getName())
                        .setRefreshPolicy(WriteRequest.RefreshPolicy.IMMEDIATE)
                        .source(lockObject(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), now()));
                highLevelClient.index(ir, RequestOptions.DEFAULT);
                return true;
            } catch (IOException e) {
                return false;
            } catch (ElasticsearchException ese) {
                if (ese.status() == RestStatus.CONFLICT) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public boolean updateRecord(LockConfiguration lockConfiguration) {
            try {
                UpdateRequest ur = new UpdateRequest()
                        .index(index)
                        .type(type)
                        .id(lockConfiguration.getName())
                        .script(new Script(ScriptType.INLINE,
                                "painless",
                                UPDATE_SCRIPT,
                                lockObject(lockConfiguration.getName(),
                                        lockConfiguration.getLockAtMostUntil(),
                                        now()))
                        );
                UpdateResponse res = highLevelClient.update(ur, RequestOptions.DEFAULT);
                return res.getResult() != DocWriteResponse.Result.NOOP;
            } catch (IOException e) {
                return false;
            } catch (ElasticsearchException ese) {
                if (ese.status() == RestStatus.CONFLICT) {
                    return false;
                }
            }
            return false;
        }

        @Override
        public void unlock(LockConfiguration lockConfiguration) {
            // Set lockUtil to now or lockAtLeastUntil whichever is later
            try {
                UpdateRequest ur = new UpdateRequest()
                        .index(index)
                        .type(type)
                        .id(lockConfiguration.getName())
                        .script(new Script(ScriptType.INLINE,
                                "painless",
                                "ctx._source.lockUntil = params.unlockTime",
                                Collections.singletonMap("unlockTime", lockConfiguration.getUnlockTime().toEpochMilli())));
                highLevelClient.update(ur, RequestOptions.DEFAULT);
            } catch (IOException e) {
                // TODO
            } catch (ElasticsearchException ese) {
                if (ese.status() == RestStatus.CONFLICT) {
                    //TODO
                }
            }
        }

        private Date now() {
            return new Date();
        }

        private Map<String, Object> lockObject(String name, Instant lockUntil, Date lockedAt) throws IOException {
            Map<String, Object> lock = new HashMap<>();
            lock.put(NAME, name);
            lock.put(LOCKED_BY, hostname);
            lock.put(LOCKED_AT, lockedAt.getTime());
            lock.put(LOCK_UNTIL, lockUntil.toEpochMilli());
            return lock;
        }
    }
}
