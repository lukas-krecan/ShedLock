package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;

import net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider.Configuration;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetSocketAddress;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test uses local instance of Cassandra running on localhost at port 9042 using keyspace shedlock and table lock
 *
 * @see net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider
 */
@Testcontainers
public class CassandraLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    private static CqlSession session;

    @Container
    public static final MyCassandraContainer cassandra = new MyCassandraContainer()
        .withInitScript("shedlock.cql")
        .withEnv("CASSANDRA_DC", "local")
        .withEnv("CASSANDRA_ENDPOINT_SNITCH", "GossipingPropertyFileSnitch");

    @BeforeAll
    public static void startCassandra() {
        String containerIpAddress = cassandra.getContainerIpAddress();
        int containerPort = cassandra.getMappedPort(9042);
        InetSocketAddress containerEndPoint = new InetSocketAddress(containerIpAddress, containerPort);

        session = CqlSession.builder()
            .addContactPoint(containerEndPoint)
            .withLocalDatacenter("local")
            .withKeyspace("shedlock")
            .build();
    }

    @AfterEach
    public void after() {
        session.execute(QueryBuilder.truncate(CassandraLockProvider.DEFAULT_TABLE).build());
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new CassandraLockProvider(session);
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
        CassandraStorageAccessor cassandraStorageAccessor = new CassandraStorageAccessor(Configuration.builder().withCqlSession(session).withConsistencyLevel(ConsistencyLevel.QUORUM).withTableName(CassandraLockProvider.DEFAULT_TABLE).build());
        return cassandraStorageAccessor.find(lockName).get();
    }

    private static class MyCassandraContainer extends CassandraContainer<MyCassandraContainer> {

    }
}
