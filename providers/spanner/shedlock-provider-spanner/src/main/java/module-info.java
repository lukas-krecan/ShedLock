/**
 * ShedLock Google Cloud Spanner provider module.
 *
 * This module provides Google Cloud Spanner-based lock provider implementation
 * using the Google Cloud Spanner client library.
 */
module net.javacrumbs.shedlock.provider.spanner {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires google.cloud.spanner;
    requires google.cloud.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.spanner;
}
