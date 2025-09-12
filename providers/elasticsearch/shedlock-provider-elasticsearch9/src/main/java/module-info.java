/**
 * ShedLock Elasticsearch 9 provider module.
 *
 * This module provides Elasticsearch-based lock provider implementation
 * using the Elasticsearch Java client version 9.x.
 */
module net.javacrumbs.shedlock.provider.elasticsearch9 {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires elasticsearch.java;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.elasticsearch9;
}
