package net.javacrumbs.shedlock.provider.s3v2;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsRequest;

/**
 * Integration test uses local instance of LocalStack S3 running on localhost at
 * port 9042 using bucket shedlock and folder shedlock
 *
 * @see net.javacrumbs.shedlock.provider.s3v2.S3LockProvider
 */
@Testcontainers
public class S3LockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    @Container
    static final MyLocalStackS3Container localStackS3 = new MyLocalStackS3Container();

    private static S3Client s3Client;
    private static final String BUCKET_NAME = "my-bucket";
    private static final String OBJECT_PREFIX = "prefix";

    @BeforeAll
    public static void startLocalStackS3() {
        s3Client = S3Client.builder()
                .endpointOverride(URI.create(localStackS3.getEndpoint().toString()))
                .credentialsProvider(
                        () -> AwsBasicCredentials.create(localStackS3.getAccessKey(), localStackS3.getSecretKey()))
                .region(Region.of(localStackS3.getRegion()))
                .build();
    }

    @BeforeEach
    public void before() {
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    }

    @AfterEach
    public void after() {
        var listObjectsResponse = s3Client.listObjects(
                ListObjectsRequest.builder().bucket(BUCKET_NAME).build());

        listObjectsResponse
                .contents()
                .forEach(s3Object -> s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(BUCKET_NAME)
                        .key(s3Object.key())
                        .build()));
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new S3LockProvider(s3Client, BUCKET_NAME, OBJECT_PREFIX);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Lock lock = findLock(lockName);
        assertThat(lock.lockUntil()).isBefore(ClockProvider.now());
        assertThat(lock.lockedAt()).isBefore(ClockProvider.now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        Lock lock = findLock(lockName);
        assertThat(lock.lockUntil()).isAfter(ClockProvider.now());
        assertThat(lock.lockedAt()).isBefore(ClockProvider.now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    private Lock findLock(String lockName) {
        return new S3StorageAccessor(s3Client, BUCKET_NAME, OBJECT_PREFIX)
                .find(lockName, "test")
                .get();
    }

    private static class MyLocalStackS3Container extends LocalStackContainer {
        private MyLocalStackS3Container() {
            super(DockerImageName.parse("localstack/localstack:s3-latest"));
        }
    }
}
