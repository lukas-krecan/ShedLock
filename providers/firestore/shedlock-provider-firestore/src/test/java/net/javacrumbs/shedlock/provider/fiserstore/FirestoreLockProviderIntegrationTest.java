package net.javacrumbs.shedlock.provider.fiserstore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.provider.firestore.FirestoreLockProvider;
import net.javacrumbs.shedlock.provider.firestore.FirestoreStorageAccessor;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class FirestoreLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    private static final String PROJECT_ID = "shedlock-provider-datastore-test";
    private static final String COLLECTION_NAME = "shedlock";

    @Container
    public static final GenericContainer<?> firestoreEmulator = new GenericContainer<>("mtlynch/firestore-emulator")
            .withExposedPorts(8080)
            .withEnv("FIRESTORE_PROJECT_ID", PROJECT_ID);

    private Firestore firestore;
    private FirestoreLockProvider provider;
    private FirestoreStorageAccessor accessor;

    @BeforeEach
    void setUp() {
        String host = firestoreEmulator.getHost();
        Integer port = firestoreEmulator.getFirstMappedPort();
        String emulatorHost = host + ":" + port;
        System.setProperty("FIRESTORE_EMULATOR_HOST", emulatorHost);
        FirestoreOptions options = FirestoreOptions.newBuilder()
                .setProjectId(PROJECT_ID)
                .setHost(emulatorHost)
                .setCredentials(NoCredentials.getInstance())
                .build();
        firestore = options.getService();
        FirestoreLockProvider.Configuration config = FirestoreLockProvider.Configuration.builder()
                .withFirestore(firestore)
                .withCollectionName(COLLECTION_NAME)
                .withFieldNames(new FirestoreLockProvider.FieldNames("until", "at", "by"))
                .build();
        accessor = new FirestoreStorageAccessor(config);
        provider = new FirestoreLockProvider(config);
    }

    @AfterEach
    void cleanUp() throws Exception {
        var locks = firestore.collection(COLLECTION_NAME).get().get().getDocuments();
        for (var doc : locks) {
            doc.getReference().delete().get();
        }
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return provider;
    }

    @Override
    protected void assertLocked(@NotNull String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName).orElseThrow();
        assertThat(lock.lockedUntil()).isAfter(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertUnlocked(@NotNull String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName).orElseThrow();
        assertThat(lock.lockedUntil()).isBefore(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    private Optional<FirestoreStorageAccessor.Lock> findLock(String lockName) {
        try {
            return accessor.findLock(lockName);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed to get lock from Firestore", e);
        }
    }
}
