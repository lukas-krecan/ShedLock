package net.javacrumbs.shedlock.provider.gcs;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;
import net.javacrumbs.shedlock.support.LockException;

class GcsAccessor extends AbstractStorageAccessor {
    private static final String LOCK_FILE_CONTENT = "_lock";
    private static final String LOCK_UNTIL = "lockUntil";
    private static final String LOCKED_AT = "lockedAt";
    private static final String LOCKED_BY = "lockedBy";
    private static final String LOCK_NAME = "lockName";

    private final Storage storage;
    private final String bucketName;

    GcsAccessor(Storage storage, String bucketName) {
        this.storage = storage;
        this.bucketName = bucketName;
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {

        try {

            BlobId blobId = BlobId.of(bucketName, lockConfiguration.getName());

            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setMetadata(createMetadata(lockConfiguration, now(), getHostname()))
                    .build();

            storage.create(blobInfo, LOCK_FILE_CONTENT.getBytes(), Storage.BlobTargetOption.doesNotExist());

            logger.debug("insertRecord success for {}", lockConfiguration.getName());

            return true;

        } catch (StorageException e) {

            if (e.getCode() == 412) { // Precondition failed

                logger.debug("insertRecord failed (exists) for {}", lockConfiguration.getName());

                return false;
            }

            throw new LockException("Could not insert record", e);
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {

        return find(lockConfiguration.getName())
                .map(lock -> {
                    if (lock.lockUntil().isBefore(now())) {

                        boolean updated = update(lockConfiguration, lock, now(), getHostname());

                        logger.debug("updateRecord result for {}: {}", lockConfiguration.getName(), updated);

                        return updated;
                    }

                    logger.debug("updateRecord skipped (not expired) for {}", lockConfiguration.getName());

                    return false;
                })
                .orElseGet(() -> {
                    boolean inserted = insertRecord(lockConfiguration);

                    logger.debug(
                            "updateRecord -> insertRecord result for {}: {}", lockConfiguration.getName(), inserted);

                    return inserted;
                });
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        return find(lockConfiguration.getName())
                .map(lock -> {
                    if (lock.lockedBy().equals(getHostname())
                            && lock.lockUntil().isAfter(now())) {
                        return update(lockConfiguration, lock, lock.lockedAt(), getHostname());
                    }
                    return false;
                })
                .orElse(false);
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        find(lockConfiguration.getName()).ifPresent(lock -> {
            if (lock.lockedBy().equals(getHostname())) {
                update(
                        lockConfiguration.getName(),
                        lock,
                        lock.lockedAt(),
                        getHostname(),
                        lockConfiguration.getUnlockTime());
            }
        });
    }

    private boolean update(LockConfiguration lockConfiguration, GcsLock lock, Instant lockedAt, String lockedBy) {
        return update(lockConfiguration.getName(), lock, lockedAt, lockedBy, lockConfiguration.getLockAtMostUntil());
    }

    private boolean update(String name, GcsLock lock, Instant lockedAt, String lockedBy, Instant lockUntil) {
        try {
            BlobId blobId = BlobId.of(bucketName, name);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setMetadata(createMetadata(name, lockUntil, lockedAt, lockedBy))
                    .build();

            storage.create(
                    blobInfo,
                    LOCK_FILE_CONTENT.getBytes(),
                    Storage.BlobTargetOption.generationMatch(lock.generation()));
            return true;
        } catch (StorageException e) {
            if (e.getCode() == 412) { // Precondition failed
                return false;
            }
            throw new LockException("Could not update record", e);
        }
    }

    private Optional<GcsLock> find(String name) {
        Blob blob = storage.get(BlobId.of(bucketName, name));
        if (blob == null) {
            return Optional.empty();
        }
        Map<String, String> metadata = blob.getMetadata();
        return Optional.of(new GcsLock(
                Instant.parse(requireNonNull(metadata.get(LOCK_UNTIL))),
                Instant.parse(requireNonNull(metadata.get(LOCKED_AT))),
                requireNonNull(metadata.get(LOCKED_BY)),
                blob.getGeneration()));
    }

    private Map<String, String> createMetadata(LockConfiguration lockConfiguration, Instant lockedAt, String lockedBy) {
        return createMetadata(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil(), lockedAt, lockedBy);
    }

    private Map<String, String> createMetadata(String name, Instant lockUntil, Instant lockedAt, String lockedBy) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put(LOCK_NAME, name);
        metadata.put(LOCK_UNTIL, lockUntil.toString());
        metadata.put(LOCKED_AT, lockedAt.toString());
        metadata.put(LOCKED_BY, lockedBy);
        return metadata;
    }
}
