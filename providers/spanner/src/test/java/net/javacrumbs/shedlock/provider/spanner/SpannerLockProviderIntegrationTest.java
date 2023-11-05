package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.Timestamp;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

import static org.assertj.core.api.Assertions.assertThat;

import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.time.Instant;

class SpannerLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private SpannerStorageAccessor accessor;
    private SpannerLockProvider provider;


    @BeforeEach
    void setUp() {
    }

    @AfterEach
    void tearDown() {
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return null;
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
        return this.accessor.findLock(lockName).get();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp.toSqlTimestamp().toInstant();
    }

}
