package net.javacrumbs.shedlock.provider.gcs;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.time.Instant;
import java.util.Map;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public class GcsLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    private static final String BUCKET_NAME = "shedlock-test";

    @Container
    private static final GenericContainer<?> gcsEmulator = new GenericContainer<>(
                    DockerImageName.parse("fsouza/fake-gcs-server"))
            .withExposedPorts(4443)
            .withCommand("-scheme", "http");

    private static Storage storage;

    @BeforeAll
    public static void setUpAll() {

        storage = StorageOptions.newBuilder()
                .setHost("http://" + gcsEmulator.getHost() + ":" + gcsEmulator.getMappedPort(4443))
                .setProjectId("test-project")
                .setCredentials(NoCredentials.getInstance())
                .build()
                .getService();
    }

    @BeforeEach
    public void setUp() {
        if (storage.get(BUCKET_NAME) == null) {
            storage.create(BucketInfo.of(BUCKET_NAME));
        }
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new GcsLockProvider(storage, BUCKET_NAME);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        GcsLock lock = findLock(lockName);
        assertThat(lock.lockUntil()).isBeforeOrEqualTo(ClockProvider.now());
        assertThat(lock.lockedAt()).isBeforeOrEqualTo(ClockProvider.now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        GcsLock lock = findLock(lockName);
        assertThat(lock.lockUntil()).isAfter(ClockProvider.now());
        assertThat(lock.lockedAt()).isBeforeOrEqualTo(ClockProvider.now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    private GcsLock findLock(String lockName) {
        var blob = storage.get(BUCKET_NAME, lockName);
        Map<String, String> metadata = blob.getMetadata();
        return new GcsLock(
                Instant.parse(requireNonNull(metadata.get("lockUntil"))),
                Instant.parse(requireNonNull(metadata.get("lockedAt"))),
                requireNonNull(metadata.get("lockedBy")),
                blob.getGeneration());
    }
}
