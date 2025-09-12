/**
 * ShedLock Cassandra provider module.
 *
 * This module provides Apache Cassandra-based lock provider implementation
 * using the DataStax Java driver.
 */
module net.javacrumbs.shedlock.provider.cassandra {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires java.driver.core;
    requires java.driver.query.builder;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.cassandra;
}
