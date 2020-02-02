package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.cassandraunit.CQLDataLoader;
import org.cassandraunit.dataset.cql.ClassPathCQLDataSet;
import org.cassandraunit.utils.EmbeddedCassandraServerHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;

import static java.time.Instant.now;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test uses local instance of Cassandra running on localhost at port 9042 using keyspace shedlock and table lock
 *
 * @see net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider
 */
public class CassandraLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    private static CqlSession cqlSession;

    @BeforeAll
    public static void startCassandra() throws IOException, InterruptedException {
        EmbeddedCassandraServerHelper.startEmbeddedCassandra();
        cqlSession = EmbeddedCassandraServerHelper.getSession();
        new CQLDataLoader(cqlSession).load(new ClassPathCQLDataSet("shedlock.cql", "shedlock"));
    }

    @AfterEach
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
        CassandraStorageAccessor cassandraStorageAccessor = new CassandraStorageAccessor(cqlSession, CassandraLockProvider.DEFAULT_TABLE, ConsistencyLevel.QUORUM);
        return cassandraStorageAccessor.find(lockName).get();
    }
}
