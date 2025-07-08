package net.javacrumbs.shedlock.provider.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FirestoreLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    @Container
    public static final FirestoreEmulatorContainer firestoreEmulator;

    static {
        DockerImageName googleCloudCliImage =
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:425.0.0-emulators");
        firestoreEmulator = new FirestoreEmulatorContainer(googleCloudCliImage).withReuse(true);
    }

    private Firestore firestore;
    private FirestoreLockProvider.Configuration configuration;
    private FirestoreStorageAccessor accessor;
    private FirestoreLockProvider provider;

    @BeforeEach
    void init() {
        // Set the environment variable to tell the Firestore client to use the emulator
        String emulatorEndpoint = firestoreEmulator.getEmulatorEndpoint();

        this.firestore = FirestoreOptions.newBuilder()
                .setCredentials(NoCredentials.getInstance())
                .setProjectId("shedlock-provider-firestore-test")
                .setHost(emulatorEndpoint)
                .build()
                .getService();
        this.configuration = FirestoreLockProvider.Configuration.builder()
                .withFirestore(firestore)
                .withCollectionName("shedlock")
                .withFieldNames(new FirestoreLockProvider.FieldNames("lockUntil", "lockedAt", "lockedBy"))
                .build();
        this.accessor = new FirestoreStorageAccessor(this.configuration);
        this.provider = new FirestoreLockProvider(this.configuration);
    }

    @AfterEach
    void tearDown() {
        try {
            // Delete all documents in the collection
            firestore
                    .collection(this.configuration.getCollectionName())
                    .get()
                    .get()
                    .getDocuments()
                    .forEach(document -> {
                        try {
                            document.getReference().delete().get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new RuntimeException("Failed to delete document", e);
                        } catch (ExecutionException e) {
                            throw new RuntimeException("Failed to delete document", e);
                        }
                    });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to clean up Firestore collection", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to clean up Firestore collection", e);
        }
    }

    @Override
    protected void assertUnlocked(String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName).orElseThrow();
        assertThat(lock.lockedUntil()).isBefore(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName).orElseThrow();
        assertThat(lock.lockedUntil()).isAfter(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return this.provider;
    }

    Optional<FirestoreStorageAccessor.Lock> findLock(String lockName) {
        return this.accessor.findLock(lockName);
    }
}
