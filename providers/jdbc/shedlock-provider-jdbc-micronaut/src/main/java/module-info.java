/**
 * ShedLock JDBC Micronaut provider module.
 *
 * This module provides a JDBC-based lock provider implementation
 * specifically designed for Micronaut applications using Micronaut Data
 * transaction management.
 */
module net.javacrumbs.shedlock.provider.jdbc.micronaut {
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.jdbc.internal;
    requires io.micronaut.data.micronaut_data_tx;
    requires net.javacrumbs.shedlock.provider.sql;
    requires org.slf4j;
    requires org.jspecify;

    // Export the main provider package for public use
    exports net.javacrumbs.shedlock.provider.jdbc.micronaut;
}
