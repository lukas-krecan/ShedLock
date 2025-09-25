package net.javacrumbs.shedlock.provider.spanner;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.Timestamp;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import java.time.Instant;
import java.util.List;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import org.junit.jupiter.api.AfterEach;

class SpannerLockProviderIntegrationTest extends AbstractSpannerStorageBasedLockProviderIntegrationTest {

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
        SpannerStorageAccessor.Lock lock = findLock(lockName);

        assertThat(toInstant(lock.lockedUntil())).isBefore(ClockProvider.now());
        assertThat(toInstant(lock.lockedAt())).isBefore(ClockProvider.now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        SpannerStorageAccessor.Lock lock = findLock(lockName);

        assertThat(toInstant(lock.lockedUntil())).isAfter(ClockProvider.now());
        assertThat(toInstant(lock.lockedAt())).isBefore(ClockProvider.now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    private SpannerStorageAccessor.Lock findLock(String lockName) {
        return nonTransactionFindLock(lockName).get();
    }

    private void cleanLockTable() {
        List<Mutation> mutations = List.of(Mutation.delete("shedlock", KeySet.all()));
        getDatabaseClient().write(mutations);
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }
}
