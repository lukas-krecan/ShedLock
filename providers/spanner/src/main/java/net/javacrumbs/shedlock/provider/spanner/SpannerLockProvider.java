package net.javacrumbs.shedlock.provider.spanner;

import com.google.cloud.spanner.DatabaseClient;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.support.annotation.NonNull;

public class SpannerLockProvider extends StorageBasedLockProvider {

    public SpannerLockProvider(@NonNull DatabaseClient databaseClient) {
        super(new SpannerStorageAccessor(databaseClient));
    }
}
