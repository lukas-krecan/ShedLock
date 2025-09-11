/**
 * ShedLock Firestore provider module.
 *
 * This module provides Google Cloud Firestore-based lock provider implementation
 * using the Google Cloud Firestore client library.
 */
module net.javacrumbs.shedlock.provider.firestore {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires google.cloud.firestore;
    requires google.cloud.core;
    requires com.google.api.apicommon;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.firestore;
}
