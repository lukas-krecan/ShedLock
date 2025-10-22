package net.javacrumbs.shedlock.provider.s3v2;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * Implementation of StorageAccessor for S3 as a lock storage backend.
 * Manages locks using S3 objects with metadata for expiration and conditional writes.
 */
class S3StorageAccessor extends AbstractStorageAccessor {

    private static final String LOCK_UNTIL = "lock-until";
    private static final String LOCKED_AT = "locked-at";
    private static final String LOCKED_BY = "locked-by";
    private static final int PRECONDITION_FAILED = 412;

    private final S3Client s3Client;
    private final String bucketName;
    private final String objectPrefix;

    public S3StorageAccessor(S3Client s3Client, String bucketName, String objectPrefix) {
        this.s3Client = s3Client;
        this.bucketName = bucketName;
        this.objectPrefix = objectPrefix;
    }

    /**
     * Finds the lock in the S3 bucket.
     */
    Optional<Lock> find(String name, String action) {
        try {
            HeadObjectResponse metadataResponse = getExistingMetadata(name);

            Map<String, String> metadata = metadataResponse.metadata();

            Instant lockUntil = Instant.parse(metadata.get(LOCK_UNTIL));
            Instant lockedAt = Instant.parse(metadata.get(LOCKED_AT));
            String lockedBy = requireNonNull(metadata.get(LOCKED_BY));
            String eTag = metadataResponse.eTag();

            logger.debug("Lock found. action: {}, name: {}, lockUntil: {}, e-tag: {}", action, name, lockUntil, eTag);
            return Optional.of(new Lock(lockUntil, lockedAt, lockedBy, eTag));
        } catch (AwsServiceException e) {
            if (e.statusCode() == 404) {
                logger.debug("Lock not found. action: {}, name: {}", action, name);
                return Optional.empty();
            }
            throw new LockException(e);
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
            Map<String, String> metadata = createMetadata(lockConfiguration.getLockAtMostUntil(), now(), getHostname());

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName(name))
                    .metadata(metadata)
                    .ifNoneMatch("*")
                    .build();

            s3Client.putObject(request, getLockContent());
            logger.debug("Lock created successfully. name: {}, metadata: {}", name, metadata);
            return true;
        } catch (AwsServiceException e) {
            if (e.statusCode() == PRECONDITION_FAILED) {
                logger.debug("Lock already in use. name: {}", name);
                return false;
            } else {
                logger.warn("Failed to create lock. name: {}", name, e);
            }
            throw new LockException(e);
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName(), "updateRecord");
        if (lock.isEmpty()) {
            logger.warn("Update skipped. Lock not found. name: {}, lock: {}", lockConfiguration.getName(), lock);
            return false;
        }
        if (lock.get().lockUntil().isAfter(now())) {
            logger.debug("Update skipped. Lock still valid. name: {}, lock: {}", lockConfiguration.getName(), lock);
            return false;
        }

        Map<String, String> newMetadata = createMetadata(lockConfiguration.getLockAtMostUntil(), now(), getHostname());
        return replaceObjectMetadata(
                lockConfiguration.getName(), newMetadata, lock.get().eTag(), "updateRecord");
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName(), "unlock");
        if (lock.isEmpty()) {
            logger.warn("Unlock skipped. Lock not found. name: {}, lock: {}", lockConfiguration.getName(), lock);
            return;
        }

        updateUntil(lockConfiguration.getName(), lock.get(), lockConfiguration.getUnlockTime(), "unlock");
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        Optional<Lock> lock = find(lockConfiguration.getName(), "extend");
        if (lock.isEmpty()
                || lock.get().lockUntil().isBefore(now())
                || !lock.get().lockedBy().equals(getHostname())) {
            logger.debug(
                    "Extend skipped. Lock invalid or not owned by host. name: {}, lock: {}",
                    lockConfiguration.getName(),
                    lock);
            return false;
        }

        return updateUntil(lockConfiguration.getName(), lock.get(), lockConfiguration.getLockAtMostUntil(), "extend");
    }

    private boolean updateUntil(String name, Lock lock, Instant until, String action) {
        var existingMetadata = getExistingMetadata(name);

        Map<String, String> newMetadata =
                createMetadata(until, Instant.parse(existingMetadata.metadata().get(LOCKED_AT)), getHostname());

        return replaceObjectMetadata(name, newMetadata, lock.eTag(), action);
    }

    private HeadObjectResponse getExistingMetadata(String name) {
        return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName(name))
                .build());
    }

    private boolean replaceObjectMetadata(String name, Map<String, String> newMetadata, String eTag, String action) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName(name))
                .metadata(newMetadata)
                .ifMatch(eTag)
                .build();

        try {
            PutObjectResponse response = s3Client.putObject(request, getLockContent());
            logger.debug(
                    "Lock {} successfully. name: {}, old e-tag: {}, new e-tag: {}",
                    action,
                    name,
                    eTag,
                    response.eTag());
            return true;
        } catch (AwsServiceException e) {
            if (e.statusCode() == PRECONDITION_FAILED) {
                logger.debug("Lock not exists to {}. name: {}, e-tag {}", action, name, eTag);
                return false;
            } else {
                logger.warn("Failed to {} lock. name: {}", action, name, e);
                throw new LockException(e);
            }
        }
    }

    private static RequestBody getLockContent() {
        var uuid = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        return RequestBody.fromBytes(bb.array());
    }

    private Map<String, String> createMetadata(Instant lockUntil, Instant lockedAt, String lockedBy) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(LOCK_UNTIL, lockUntil.toString());
        metadata.put(LOCKED_AT, lockedAt.toString());
        metadata.put(LOCKED_BY, lockedBy);
        return metadata;
    }

    private String objectName(String name) {
        return objectPrefix + name;
    }
}
