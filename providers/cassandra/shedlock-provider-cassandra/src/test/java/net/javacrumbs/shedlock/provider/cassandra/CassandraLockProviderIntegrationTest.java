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
package net.javacrumbs.shedlock.provider.cassandra;

import com.datastax.oss.driver.api.core.CqlIdentifier;
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

import static com.datastax.oss.driver.api.core.CqlIdentifier.fromCql;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider.DEFAULT_TABLE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test uses local instance of Cassandra running on localhost at port 9042 using keyspace shedlock and table lock
 *
 * @see net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider
 */
@Testcontainers
public class CassandraLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    public static final CqlIdentifier KEYSPACE = fromCql("shedlock");
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
            .build();
    }

    @AfterEach
    public void after() {
        session.execute(QueryBuilder.truncate(KEYSPACE, fromCql(DEFAULT_TABLE)).build());
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new CassandraLockProvider(
            Configuration.builder()
                .withCqlSession(session)
                .withKeyspace(KEYSPACE)
                .withTableName("lock")
                .build()
        );
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
        CassandraStorageAccessor cassandraStorageAccessor = new CassandraStorageAccessor(
            Configuration.builder().withCqlSession(session).withKeyspace(KEYSPACE).withTableName(DEFAULT_TABLE).build()
        );
        return cassandraStorageAccessor.find(lockName).get();
    }

    private static class MyCassandraContainer extends CassandraContainer<MyCassandraContainer> {

    }
}
