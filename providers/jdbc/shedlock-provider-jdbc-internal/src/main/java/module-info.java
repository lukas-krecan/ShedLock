/**
 * ShedLock JDBC internal provider module.
 *
 * This module provides internal JDBC-based lock provider implementation
 * that serves as a base for other JDBC providers.
 */
module net.javacrumbs.shedlock.provider.jdbc.internal {
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.sql;
    requires static org.jspecify;
    requires org.slf4j;

    // Allow JDBC driver service loading
    uses java.sql.Driver;

    // Export provider packages only to other ShedLock modules
    exports net.javacrumbs.shedlock.provider.jdbc.internal to
            net.javacrumbs.shedlock.provider.jdbc,
            net.javacrumbs.shedlock.provider.jdbc.micronaut;
}
