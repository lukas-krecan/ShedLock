package net.javacrumbs.shedlock.provider.s3;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration test uses local instance of LocalStack S3 running on localhost at
 * port 9042 using bucket shedlock and folder shedlock
 *
 * @see net.javacrumbs.shedlock.provider.s3.S3LockProvider
 */
@Testcontainers
public class S3LockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    @Container
    static final MyLocalStackS3Container localStackS3 = new MyLocalStackS3Container();

    private static AmazonS3 s3Client;
    private static final String BUCKET_NAME = "my-bucket";
    private static final String OBJECT_PREFIX = "prefix";

    @BeforeAll
    public static void startLocalStackS3() {
        s3Client = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(
                        localStackS3.getEndpoint().toString(), localStackS3.getRegion()))
                .withCredentials(new AWSStaticCredentialsProvider(
                        new BasicAWSCredentials(localStackS3.getAccessKey(), localStackS3.getSecretKey())))
                .build();
    }

    @BeforeEach
    public void before() {
        s3Client.createBucket(BUCKET_NAME);
    }

    @AfterEach
    public void after() {
        s3Client.listObjects(BUCKET_NAME).getObjectSummaries().forEach(obj -> {
            s3Client.deleteObject(BUCKET_NAME, obj.getKey());
        });
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return new S3LockProvider(s3Client, BUCKET_NAME, OBJECT_PREFIX);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Lock lock = findLock(lockName);
        assertThat(lock.lockUntil()).isBefore(now());
        assertThat(lock.lockedAt()).isBefore(now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        Lock lock = findLock(lockName);
        assertThat(lock.lockUntil()).isAfter(now());
        assertThat(lock.lockedAt()).isBefore(now());
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    private Lock findLock(String lockName) {
        return new S3StorageAccessor(s3Client, BUCKET_NAME, OBJECT_PREFIX)
                .find(lockName, "test")
                .get();
    }

    private static class MyLocalStackS3Container extends LocalStackContainer {
        public MyLocalStackS3Container() {
            super(DockerImageName.parse("localstack/localstack:4.0.3"));
        }
    }
}
