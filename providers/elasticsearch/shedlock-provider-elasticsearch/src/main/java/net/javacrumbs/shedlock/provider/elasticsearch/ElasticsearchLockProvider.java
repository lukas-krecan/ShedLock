package net.javacrumbs.shedlock.provider.elasticsearch;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.StorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.elasticsearch.client.RestHighLevelClient;

import java.util.Date;

/**
 *  Lock using ElasticSearch &gt;= 6.4.0.
 *  Requires elasticsearch-rest-high-level-client &gt; 6.4.0
 * <p>
 * It uses a collection that contains documents like this:
 * <pre>
 * {
 *    "name" : "lock name",
 *    "lockUntil" :  {
 *      "type":   "date",
 *      "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
 *    },
 *    "lockedAt" : {
 *      "type":   "date",
 *      "format": "yyyy-MM-dd HH:mm:ss||yyyy-MM-dd||epoch_millis"
 *    }:
 *    "lockedBy" : "host name"
 * }
 * </pre>
 *
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
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String NAME = "name";

    protected ElasticsearchLockProvider(StorageAccessor storageAccessor) {
        super(storageAccessor);
    }

    public ElasticsearchLockProvider(RestHighLevelClient highLevelClient, String hostname, String shedLockIndex) {
        this(new ElasticAccessor(highLevelClient));
    }

    public ElasticsearchLockProvider(RestHighLevelClient highLevelClient, String hostname) {
        this(highLevelClient, hostname, "shedlock");
    }

    static class ElasticAccessor extends AbstractStorageAccessor {

        private final RestHighLevelClient highLevelClient;
        private final String hostname;

        ElasticAccessor(RestHighLevelClient highLevelClient) {
            this.highLevelClient = highLevelClient;
            this.hostname = getHostname();
        }

        @Override
        public boolean insertRecord(LockConfiguration lockConfiguration) {
            // TODO
            return false;
        }

        @Override
        public boolean updateRecord(LockConfiguration lockConfiguration) {
            // TODO
            return false;
        }

        @Override
        public void unlock(LockConfiguration lockConfiguration) {
            // TODO
        }

        private Date now() {
            return new Date();
        }
    }
}
