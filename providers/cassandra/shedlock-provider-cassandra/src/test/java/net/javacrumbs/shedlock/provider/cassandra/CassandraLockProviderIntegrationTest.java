package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.cassandraunit.CassandraCQLUnit;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.junit.Before;
import org.junit.Rule;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test uses local instance of Cassandra running on localhost at port 9042 using keyspace shedlock and table lock
 *
 * @see net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider
 */
public class CassandraLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    @Rule
    public CassandraCQLUnit cassandraCQLUnit = new CassandraCQLUnit(new ClassPathCQLDataSet("shedlock.cql", "shedlock"));

    private CqlSession cqlSession;

    @Before
    public void before() {
        cqlSession = cassandraCQLUnit.getSession();
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new CassandraLockProvider(cqlSession);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Lock lock = findLock(lockName);
        assertThat(lock.getLockUntil()).isBefore(now());
        assertThat(lock.getLockedAt()).isBefore(now());
        assertThat(lock.getLockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        Lock lock = findLock(lockName);
        assertThat(lock.getLockUntil()).isAfter(now());
        assertThat(lock.getLockedAt()).isBefore(now());
        assertThat(lock.getLockedBy()).isNotEmpty();
    }

    private Lock findLock(String lockName) {
        CassandraStorageAccessor cassandraStorageAccessor = new CassandraStorageAccessor(cqlSession, CassandraLockProvider.DEFAULT_TABLE, ConsistencyLevel.QUORUM);
        return cassandraStorageAccessor.find(lockName).get();
    }
}
