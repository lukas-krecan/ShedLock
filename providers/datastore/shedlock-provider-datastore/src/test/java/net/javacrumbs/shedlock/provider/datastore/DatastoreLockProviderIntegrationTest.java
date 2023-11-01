package net.javacrumbs.shedlock.provider.datastore;

import static com.google.cloud.datastore.Query.ResultType.ENTITY;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.datastore.Datastore;
import com.google.cloud.datastore.DatastoreOptions;
import com.google.cloud.datastore.Query;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.DatastoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class DatastoreLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    @Container
    public static final DatastoreEmulatorContainer datastoreEmulator;

    static {
        DockerImageName googleCloudCliImage =
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:425.0.0-emulators");
        datastoreEmulator = new DatastoreEmulatorContainer(googleCloudCliImage)
                .withFlags(
                        "--project shedlock-provider-datastore-test --host-port 0.0.0.0:8081 --use-firestore-in-datastore-mode")
                .withReuse(true);
    }

    private Datastore datastore;
    private DatastoreLockProvider.Configuration configuration;
    private DatastoreStorageAccessor accessor;
    private DatastoreLockProvider provider;

    @BeforeEach
    void init() {
        this.datastore = DatastoreOptions.newBuilder()
                .setCredentials(NoCredentials.getInstance())
                .setProjectId("shedlock-provider-datastore-test")
                .setHost("http://" + datastoreEmulator.getEmulatorEndpoint())
                .build()
                .getService();
        this.configuration = DatastoreLockProvider.Configuration.builder()
                .withDatastore(datastore)
                .withEntityName("shedlock")
                .withFieldNames(new DatastoreLockProvider.FieldNames("until", "at", "by"))
                .build();
        this.accessor = new DatastoreStorageAccessor(this.configuration);
        this.provider = new DatastoreLockProvider(this.configuration);
    }

    @AfterEach
    void tearDown() {
        var locks = String.format("select * from %s", this.configuration.getEntityName());
        var results = this.datastore.run(Query.newGqlQueryBuilder(ENTITY, locks).build());
        results.forEachRemaining(entity -> this.datastore.delete(entity.getKey()));
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

    Optional<DatastoreStorageAccessor.Lock> findLock(String lockName) {
        return this.accessor.findLock(lockName);
    }
}
