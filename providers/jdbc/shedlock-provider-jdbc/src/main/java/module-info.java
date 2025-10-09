/**
 * ShedLock JDBC provider module.
 *
 * This module provides JDBC-based lock provider implementation
 * using standard JDBC connections.
 */
module net.javacrumbs.shedlock.provider.jdbc {
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.jdbc.internal;
    requires static org.jspecify;
    requires org.slf4j;
    requires net.javacrumbs.shedlock.provider.sql;

    // Allow JDBC driver service loading
    uses java.sql.Driver;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.jdbc;
}
