package net.javacrumbs.shedlock.provider.s3;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.PutObjectResult;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of StorageAccessor for S3 as a lock storage backend.
 * Manages locks using S3 objects with metadata for expiration and conditional writes.
 */
public class S3StorageAccessor extends AbstractStorageAccessor {

    private static final String LOCK_UNTIL = "lockUntil";
    private static final String LOCKED_AT = "lockedAt";
    private static final String LOCKED_BY = "lockedBy";

    private final AmazonS3 s3Client;
    private final String bucketName;
    private final String objectPrefix;

    public S3StorageAccessor(AmazonS3 s3Client, String bucketName, String objectPrefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
    }

    /**
     * Finds the lock in the S3 bucket.
     */
    Optional<Lock> find(String name, String action) {
        try {
            ObjectMetadata metadata = s3Client.getObjectMetadata(bucketName, objectName(name));
            Instant lockUntil = Instant.parse(metadata.getUserMetaDataOf(LOCK_UNTIL));
            Instant lockedAt = Instant.parse(metadata.getUserMetaDataOf(LOCKED_AT));
            String lockedBy = metadata.getUserMetaDataOf(LOCKED_BY);
            String eTag = metadata.getETag();

            logger.debug("Lock found. action: {}, name: {}, lockUntil: {}, e-tag: {}", action, name, lockUntil, eTag);
            return Optional.of(new Lock(lockUntil, lockedAt, lockedBy, eTag));
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 404) {
                logger.debug("Lock not found. action: {}, name: {}", action, name);
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        String name = lockConfiguration.getName();
        if (find(name, "insertRecord").isPresent()) {
            logger.debug("Lock already exists. name: {}", name);
            return false;
        }

        try {
            var lockContent = UUID.randomUUID().toString().getBytes();
            ObjectMetadata metadata = createMetadata(lockConfiguration.getLockAtMostUntil(), ClockProvider.now(), getHostname());
            metadata.setContentLength(lockContent.length);

            PutObjectRequest request = new PutObjectRequest(
                    bucketName, objectName(name), new ByteArrayInputStream(lockContent), metadata
            );
            request.putCustomRequestHeader("If-None-Match", "*");

            s3Client.putObject(request);
            logger.debug("Lock created successfully. name: {}, metadata: {}", name, metadata.getUserMetadata());
            return true;
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 412) {
                logger.debug("Lock already in use. name: {}", name);
            } else {
                logger.warn("Failed to create lock. name: {}", name, e);
            }
            return false;
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName(), "updateRecord");
        if (lock.isEmpty() || lock.get().lockUntil().isAfter(ClockProvider.now())) {
            logger.debug("Update skipped. Lock still valid or not found. name: {}, lock: {}", lockConfiguration.getName(), lock);
            return false;
        }

        ObjectMetadata newMetadata = createMetadata(lockConfiguration.getLockAtMostUntil(), ClockProvider.now(), getHostname());
        return replaceObjectMetadata(lockConfiguration.getName(), newMetadata, lock.get().eTag(), "updateRecord");
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName(), "unlock");
        if (lock.isEmpty()) {
            logger.debug("Unlock skipped. Lock not found. name: {}, lock: {}", lockConfiguration.getName(), lock);
            return;
        }

        updateUntil(lockConfiguration.getName(), lock.get(), lockConfiguration.getUnlockTime(), "unlock");
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName(), "extend");
        if (lock.isEmpty()
                || lock.get().lockUntil().isBefore(ClockProvider.now())
                || !lock.get().lockedBy().equals(getHostname())) {
            logger.debug("Extend skipped. Lock invalid or not owned by host. name: {}, lock: {}", lockConfiguration.getName(), lock);
            return false;
        }

        return updateUntil(lockConfiguration.getName(), lock.get(), lockConfiguration.getLockAtMostUntil(), "extend");
    }

    private boolean updateUntil(String name, Lock lock, Instant until, String action) {
        ObjectMetadata existingMetadata = s3Client.getObjectMetadata(bucketName, objectName(name));
        ObjectMetadata newMetadata = createMetadata(until, Instant.parse(existingMetadata.getUserMetaDataOf(LOCKED_AT)), getHostname());

        return replaceObjectMetadata(name, newMetadata, lock.eTag(), action);
    }

    private boolean replaceObjectMetadata(String name, ObjectMetadata newMetadata, String eTag, String action) {
        var lockContent = UUID.randomUUID().toString().getBytes();
        newMetadata.setContentLength(lockContent.length);

        PutObjectRequest request = new PutObjectRequest(
                bucketName, objectName(name), new ByteArrayInputStream(lockContent), newMetadata
        );
        request.putCustomRequestHeader("If-Match", eTag);

        try {
            PutObjectResult response = s3Client.putObject(request);
            logger.debug("Lock {} successfully. name: {}, old e-tag: {}, new e-tag: {}", action, name, eTag, response.getETag());
            return true;
        } catch (AmazonServiceException e) {
            if (e.getStatusCode() == 412) {
                logger.debug("Lock not exists to {}. name: {}, e-tag {}", action, name, eTag);
            } else {
                logger.warn("Failed to create lock. name: {}", name, e);
            }
            return false;
        }
    }

    private ObjectMetadata createMetadata(Instant lockUntil, Instant lockedAt, String lockedBy) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.addUserMetadata(LOCK_UNTIL, lockUntil.toString());
        metadata.addUserMetadata(LOCKED_AT, lockedAt.toString());
        metadata.addUserMetadata(LOCKED_BY, lockedBy);
        return metadata;
    }

    private String objectName(String name) {
        return objectPrefix + name;
    }
}
