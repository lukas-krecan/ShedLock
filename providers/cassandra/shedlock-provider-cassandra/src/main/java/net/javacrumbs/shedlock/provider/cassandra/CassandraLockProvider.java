package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

/**
 * Cassandra Lock Provider needs a keyspace and uses a lock table
 * <br>
 * Example creating keyspace and table
 * <pre>
 * CREATE KEYSPACE shedlock with replication={'class':'SimpleStrategy', 'replication_factor':1} and durable_writes=true;
 * CREATE TABLE shedlock.lock (name text PRIMARY KEY, lockUntil timestamp, lockedAt timestamp, lockedBy text);
 * </pre>
 */
public class CassandraLockProvider extends StorageBasedLockProvider {
    static final String DEFAULT_TABLE = "lock";

    public CassandraLockProvider(@NonNull CqlSession cqlSession) {
        super(new CassandraStorageAccessor(cqlSession, DEFAULT_TABLE, ConsistencyLevel.QUORUM));
    }

    public CassandraLockProvider(@NonNull CqlSession cqlSession, @NonNull String table, @NonNull ConsistencyLevel consistencyLevel) {
        super(new CassandraStorageAccessor(cqlSession, table, consistencyLevel));
    }
}
