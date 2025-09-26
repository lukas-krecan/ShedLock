/**
 * ShedLock Hazelcast provider module.
 *
 * This module provides Hazelcast-based lock provider implementation
 * using Hazelcast distributed data structures.
 */
module net.javacrumbs.shedlock.provider.hazelcast4 {
    requires net.javacrumbs.shedlock.core;
    requires com.hazelcast.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.hazelcast4;
}
