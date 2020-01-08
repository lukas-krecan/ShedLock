package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.After;
import org.junit.Before;

import java.net.InetSocketAddress;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test uses local instance of Cassandra running on localhost at port 9042 using keyspace shedlock and table lock
 *
 * @see net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider
 */
public class CassandraLockProvderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private CqlSession cqlSession;

    @Before
    public void before() {
        cqlSession = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(CassandraLockProvider.DEFAULT_CONCACT_POINT, CassandraLockProvider.DEFAULT_PORT))
                .withLocalDatacenter(CassandraLockProvider.DEFAULT_DATACENTER)
                .withKeyspace(CassandraLockProvider.DEFAULT_KEYSPACE)
                .build();
    }

    @After
    public void after() {
        cqlSession.execute(QueryBuilder.truncate(CassandraLockProvider.DEFAULT_TABLE).build());
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
        CassandraStorageAccessor cassandraStorageAccessor = new CassandraStorageAccessor(cqlSession, CassandraLockProvider.DEFAULT_TABLE);
        return cassandraStorageAccessor.find(lockName).get();
    }
}
