/**
 * ShedLock Couchbase Java Client 3 provider module.
 *
 * This module provides Couchbase-based lock provider implementation
 * using the Couchbase Java Client 3.x library.
 */
module net.javacrumbs.shedlock.provider.couchbase.javaclient3x {
    requires net.javacrumbs.shedlock.core;
    requires com.couchbase.client.java;
    requires com.couchbase.client.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.couchbase.javaclient3;
}
