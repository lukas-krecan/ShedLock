/**
 * ShedLock JDBC provider module.
 *
 * This module provides JDBC-based lock provider implementation
 * using standard JDBC connections.
 */
module net.javacrumbs.shedlock.provider.jdbc {
    requires java.base;
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.jdbc.internal;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.jdbc;
}
