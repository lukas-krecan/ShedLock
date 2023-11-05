package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.NoCredentials;
import com.google.cloud.spanner.Database;
import com.google.cloud.spanner.DatabaseAdminClient;
import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.Instance;
import com.google.cloud.spanner.InstanceAdminClient;
import com.google.cloud.spanner.InstanceConfigId;
import com.google.cloud.spanner.InstanceId;
import com.google.cloud.spanner.InstanceInfo;
import com.google.cloud.spanner.Spanner;
import com.google.cloud.spanner.SpannerOptions;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.SpannerEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Testcontainers
public abstract class AbstractSpannerStorageBasedLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private static final String SPANNER_EMULATOR_IMAGE = "gcr.io/cloud-spanner-emulator/emulator:latest";
    private static final String PROJECT_NAME = "test-project";
    private static final String INSTANCE_NAME = "test-instance";
    private static final String DATABASE_NAME = "test-db";

    private static DatabaseClient databaseClient;

    @Container
    public static final SpannerEmulatorContainer emulator =
        new SpannerEmulatorContainer(DockerImageName.parse(SPANNER_EMULATOR_IMAGE));


    @BeforeAll
    public static void setUpSpanner() {
        Spanner spanner = createSpannerService();
        InstanceId instanceId = createInstance(spanner);
        DatabaseId databaseId = createDatabase(spanner);
        databaseClient = spanner.getDatabaseClient(databaseId);
    }

    static DatabaseClient getDatabaseClient() {
        return databaseClient;
    }

    private static Spanner createSpannerService() {
        SpannerOptions options = SpannerOptions
            .newBuilder()
            .setEmulatorHost(emulator.getEmulatorGrpcEndpoint())
            .setCredentials(NoCredentials.getInstance())
            .setProjectId(PROJECT_NAME)
            .build();

        return options.getService();
    }

    private static InstanceId createInstance(Spanner spanner) {
        InstanceConfigId instanceConfig = InstanceConfigId.of(PROJECT_NAME, "emulator-config");
        InstanceId instanceId = InstanceId.of(PROJECT_NAME, INSTANCE_NAME);
        InstanceAdminClient insAdminClient = spanner.getInstanceAdminClient();
        try {
            Instance instance = insAdminClient
                .createInstance(
                    InstanceInfo
                        .newBuilder(instanceId)
                        .setNodeCount(1)
                        .setDisplayName("Test instance")
                        .setInstanceConfigId(instanceConfig)
                        .build()
                )
                .get();
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException("Failed creating Spanner instance.", e);
        }
        return instanceId;
    }

    private static DatabaseId createDatabase(Spanner spanner) {
        DatabaseAdminClient dbAdminClient = spanner.getDatabaseAdminClient();
        try {
            Database database = dbAdminClient
                .createDatabase(
                    INSTANCE_NAME,
                    DATABASE_NAME,
                    List.of(getShedlockDdl())
                )
                .get();
            return database.getId();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException("Failed creating Spanner database.", e);
        }
    }

    private static String getShedlockDdl() {
        return
            """
                CREATE TABLE shedlock (
                    name STRING(64) NOT NULL,
                    lock_until TIMESTAMP NOT NULL,
                    locked_at TIMESTAMP NOT NULL,
                    locked_by STRING(255) NOT NULL
                    ) PRIMARY KEY (name)
                """;
    }

}
