/**
 * ShedLock JDBC internal provider module.
 *
 * This module provides internal JDBC-based lock provider implementation
 * that serves as a base for other JDBC providers.
 */
module net.javacrumbs.shedlock.provider.jdbc.internal {
    requires java.base;
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires org.slf4j;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.jdbc.internal;
}
