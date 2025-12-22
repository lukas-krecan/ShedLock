package net.javacrumbs.shedlock.provider.gcs;

import com.google.cloud.storage.Storage;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;

/**
 * Lock provider implementation for Google Cloud Storage.
 */
public class GcsLockProvider extends StorageBasedLockProvider {

    /**
     * Constructs a GcsLockProvider.
     *
     * @param storage Storage client used to interact with Google Cloud Storage.
     * @param bucketName The name of the GCS bucket where locks are stored.
     */
    public GcsLockProvider(Storage storage, String bucketName) {
        super(new GcsAccessor(storage, bucketName));
    }
}
