/**
 * ShedLock OpenSearch Java provider module.
 *
 * This module provides OpenSearch-based lock provider implementation
 * using the OpenSearch Java client library.
 */
module net.javacrumbs.shedlock.provider.opensearch.java {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires opensearch.java;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.opensearch.java;
}
