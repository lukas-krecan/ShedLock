package net.javacrumbs.shedlock.provider.hazelcast;


import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.UnknownHostException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static HazelcastInstance hazelcastInstance;

    private static HazelcastLockProvider lockProvider;

    @BeforeClass
    public static void startHazelcast() throws IOException {
        hazelcastInstance = Hazelcast.newHazelcastInstance();
        lockProvider = new HazelcastLockProvider(hazelcastInstance);
    }

    @AfterClass
    public static void stopHazelcast() throws IOException {
        hazelcastInstance.shutdown();
    }

    @After
    public void resetLockProvider() throws UnknownHostException {
        hazelcastInstance.removeDistributedObjectListener(HazelcastLockProvider.LOCK_STORE_KEY_DEFAULT);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(final String lockName) {
        assertThat(isUnlocked(lockName));
    }

    private boolean isUnlocked(final String lockName) {
        final Instant now = Instant.now();
        final HazelcastLock lock = lockProvider.getLock(lockName);
        return lock == null;
    }

    @Override
    protected void assertLocked(final String lockName) {
        assertThat(!isUnlocked(lockName));
    }


}