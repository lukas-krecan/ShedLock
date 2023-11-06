package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpannerLockProviderIntegrationTest extends AbstractSpannerStorageBasedLockProviderIntegrationTest {

    private static SpannerStorageAccessor accessor;

    @BeforeAll
    static void setUp() {
        SpannerLockProvider.Configuration configuration = SpannerLockProvider.Configuration.builder()
            .withDatabaseClient(getDatabaseClient())
            .build();
        accessor = new SpannerStorageAccessor(configuration);
    }

    @AfterEach
    void cleanUp() {
        cleanLockTable();
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new SpannerLockProvider(getDatabaseClient());
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Instant now = ClockProvider.now();
        SpannerStorageAccessor.Lock lock = findLock(lockName);

        assertThat(toInstant(lock.getLockedUntil())).isBefore(now);
        assertThat(toInstant(lock.getLockedAt())).isBefore(now);
        assertThat(lock.getLockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        Instant now = ClockProvider.now();
        SpannerStorageAccessor.Lock lock = findLock(lockName);

        assertThat(toInstant(lock.getLockedUntil())).isAfter(now);
        assertThat(toInstant(lock.getLockedAt())).isBefore(now);
        assertThat(lock.getLockedBy()).isNotEmpty();
    }

    private SpannerStorageAccessor.Lock findLock(String lockName) {
        return getDatabaseClient().readWriteTransaction().run(transactionContext ->
            accessor.findLock(transactionContext, lockName)).get();
    }

    private void cleanLockTable() {
        List<Mutation> mutations = List.of(Mutation.delete("shedlock", KeySet.all()));
        getDatabaseClient().write(mutations);
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

}
