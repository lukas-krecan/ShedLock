package net.javacrumbs.shedlock.provider.s3v2;

import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Lock provider implementation for S3.
 */
public class S3LockProvider extends StorageBasedLockProvider {

    /**
     * Constructs an S3LockProvider.
     *
     * @param s3Client S3 client used to interact with the S3 bucket.
     * @param bucketName The name of the S3 bucket where locks are stored.
     * @param objectPrefix The prefix of the S3 object lock.
     */
    public S3LockProvider(S3Client s3Client, String bucketName, String objectPrefix) {
        super(new S3StorageAccessor(s3Client, bucketName, objectPrefix));
    }

    /**
     * Constructs an S3LockProvider.
     *
     * @param s3Client S3 client used to interact with the S3 bucket.
     * @param bucketName The name of the S3 bucket where locks are stored.
     */
    public S3LockProvider(S3Client s3Client, String bucketName) {
        this(s3Client, bucketName, "shedlock/");
    }
}
