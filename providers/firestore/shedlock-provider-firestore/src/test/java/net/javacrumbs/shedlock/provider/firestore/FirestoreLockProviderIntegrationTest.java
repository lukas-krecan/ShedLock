package net.javacrumbs.shedlock.provider.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.testcontainers.gcloud.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@Disabled
class FirestoreLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    @Container
    public static final FirestoreEmulatorContainer firestoreEmulator = new FirestoreEmulatorContainer(
            DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:540.0.0-emulators"));

    private final Firestore firestore = FirestoreOptions.newBuilder()
            .setCredentials(NoCredentials.getInstance())
            .setProjectId("shedlock-provider-firestore-test")
            .setHost(firestoreEmulator.getEmulatorEndpoint())
            .build()
            .getService();
    private final FirestoreLockProvider.Configuration configuration = FirestoreLockProvider.Configuration.builder()
            .withFirestore(firestore)
            .withCollectionName("shedlock")
            .withFieldNames(new FirestoreLockProvider.FieldNames("lockUntil", "lockedAt", "lockedBy"))
            .build();
    private final FirestoreStorageAccessor accessor = new FirestoreStorageAccessor(this.configuration);
    private final FirestoreLockProvider provider = new FirestoreLockProvider(this.configuration);

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
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException("Failed to delete document", e);
                        }
                    });
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to clean up Firestore collection", e);
        }
    }

    @Override
    protected void assertUnlocked(String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName);
        assertThat(lock.lockedUntil()).isBefore(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName);
        assertThat(lock.lockedUntil()).isAfter(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return this.provider;
    }

    @Disabled
    @Override
    public void fuzzTestShouldPass() throws ExecutionException, InterruptedException {
        // It gets stuck
    }

    FirestoreStorageAccessor.Lock findLock(String lockName) {
        return this.accessor.findLock(lockName).orElseThrow();
    }
}
