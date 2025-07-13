/**
 * ShedLock JDBC Template provider module.
 *
 * This module provides Spring JDBC Template-based lock provider implementation.
 */
module net.javacrumbs.shedlock.provider.jdbctemplate {
    requires java.base;
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires spring.jdbc;
    requires spring.tx;

    // Allow JDBC driver service loading
    uses java.sql.Driver;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.jdbctemplate;
}
