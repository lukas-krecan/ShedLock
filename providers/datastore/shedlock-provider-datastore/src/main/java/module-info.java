/**
 * ShedLock Google Cloud Datastore provider module.
 *
 * This module provides Google Cloud Datastore-based lock provider implementation
 * using the Google Cloud Datastore client library.
 */
module net.javacrumbs.shedlock.provider.datastore {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires google.cloud.datastore;
    requires google.cloud.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.datastore;
}
