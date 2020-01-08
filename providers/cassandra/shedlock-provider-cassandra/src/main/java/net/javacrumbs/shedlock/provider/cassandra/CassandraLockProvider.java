package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.jetbrains.annotations.NotNull;

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

    static final String DEFAULT_CONCACT_POINT = "localhost";
    static final int DEFAULT_PORT = 9042;
    static final String DEFAULT_DATACENTER = "datacenter1";
    static final String DEFAULT_KEYSPACE = "shedlock";
    static final String DEFAULT_TABLE = "lock";

    /**
     * Default configuration contact point is localhost at port 9042 at datacenter1 using keyspace shedlock anb table lock
     */
    public CassandraLockProvider() {
        super(new CassandraStorageAccessor(DEFAULT_CONCACT_POINT, DEFAULT_PORT, DEFAULT_DATACENTER, DEFAULT_KEYSPACE, DEFAULT_TABLE));
    }

    public CassandraLockProvider(@NotNull String contactPoint, @NotNull int port, @NotNull String datacenter, @NotNull String keyspace, @NotNull String table) {
        super(new CassandraStorageAccessor(contactPoint, port, datacenter, keyspace, table));
    }

    /**
     * Using default table lock
     *
     * @param cqlSession
     */
    public CassandraLockProvider(@NotNull CqlSession cqlSession) {
        super(new CassandraStorageAccessor(cqlSession, DEFAULT_TABLE));
    }

    public CassandraLockProvider(@NotNull CqlSession cqlSession, @NotNull String table) {
        super(new CassandraStorageAccessor(cqlSession, table));
    }
}
