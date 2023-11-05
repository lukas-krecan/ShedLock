package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.DatabaseClient;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeAll;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SpannerLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private static DatabaseClient databaseClient;

    private static SpannerStorageAccessor accessor;

    @BeforeAll
    static void setUp() {
        SpannerEmulator spannerEmulator = new SpannerEmulator();
        databaseClient = spannerEmulator.getDatabaseClient();
        accessor = new SpannerStorageAccessor(databaseClient);
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new SpannerLockProvider(databaseClient);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Instant now = ClockProvider.now();
        SpannerStorageAccessor.SpannerLock lock = findLock(lockName);

        assertThat(toInstant(lock.getLockedUntil())).isBefore(now);
        assertThat(toInstant(lock.getLockedAt())).isBefore(now);
        assertThat(lock.getLockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        Instant now = ClockProvider.now();
        SpannerStorageAccessor.SpannerLock lock = findLock(lockName);

        assertThat(toInstant(lock.getLockedUntil())).isAfter(now);
        assertThat(toInstant(lock.getLockedAt())).isBefore(now);
        assertThat(lock.getLockedBy()).isNotEmpty();
    }

    private SpannerStorageAccessor.SpannerLock findLock(String lockName) {
        return databaseClient.readWriteTransaction().run(transactionContext ->
            accessor.findLock(transactionContext, lockName)).get();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp.toSqlTimestamp().toInstant();
    }

}
