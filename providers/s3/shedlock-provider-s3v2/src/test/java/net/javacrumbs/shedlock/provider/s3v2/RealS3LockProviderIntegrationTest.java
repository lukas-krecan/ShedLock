package net.javacrumbs.shedlock.provider.s3v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;

/**
 * Integration test that uses a real S3 bucket. Fill in the configuration values
 * before running it.
 *
 * @see net.javacrumbs.shedlock.provider.s3v2.S3LockProvider
 */
@Disabled
public class RealS3LockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    private static final String ACCESS_KEY_ID = "";
    private static final String SECRET_ACCESS_KEY = "";
    private static final String SESSION_TOKEN = "";
    private static final String REGION = "eu-central-1";
    private static final String BUCKET_NAME = "shedlock-test";
    private static final String OBJECT_PREFIX = "shedlock-real-s3-test/";

    private static S3Client s3Client;

    @BeforeAll
    public static void createS3Client() {
        assumeTrue(isConfigured(), "Fill in the real S3 test configuration values before running this test.");

        s3Client = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(credentials()))
                .region(Region.of(REGION))
                .build();
    }

    @AfterEach
    public void after() {
        s3Client.listObjectsV2Paginator(ListObjectsV2Request.builder()
                        .bucket(BUCKET_NAME)
                        .prefix(OBJECT_PREFIX)
                        .build())
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

    private static boolean isConfigured() {
        return !ACCESS_KEY_ID.isBlank()
                && !SECRET_ACCESS_KEY.isBlank()
                && !REGION.isBlank()
                && !BUCKET_NAME.isBlank()
                && !OBJECT_PREFIX.isBlank();
    }

    private static AwsCredentials credentials() {
        if (SESSION_TOKEN.isBlank()) {
            return AwsBasicCredentials.create(ACCESS_KEY_ID, SECRET_ACCESS_KEY);
        }
        return AwsSessionCredentials.create(ACCESS_KEY_ID, SECRET_ACCESS_KEY, SESSION_TOKEN);
    }
}
