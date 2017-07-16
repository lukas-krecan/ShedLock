package net.javacrumbs.shedlock.provider.hazelcast;


import com.hazelcast.core.HazelcastInstance;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;
import static org.assertj.core.api.Assertions.assertThat;

public class HazelcastLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static HazelcastInstance hazelcastInstance;
    private HazelcastLockProvider lockProvider;

    @BeforeClass
    public static void startMongo() throws IOException {
        hazelcastInstance = null;
    }

    @AfterClass
    public static void stopMongo() throws IOException {
        hazelcastInstance.shutdown();
    }

    @Before
    public void createLockProvider() throws UnknownHostException {
        lockProvider = new HazelcastLockProvider();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {

    }

    @Override
    protected void assertLocked(String lockName) {
    }


}