/**
 * ShedLock R2DBC provider module.
 *
 * This module provides R2DBC-based lock provider implementation
 * using reactive database connectivity.
 */
module net.javacrumbs.shedlock.provider.r2dbc {
    requires net.javacrumbs.shedlock.core;
    requires transitive net.javacrumbs.shedlock.provider.sql;
    requires r2dbc.spi;
    requires reactor.core;
    requires org.reactivestreams;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.r2dbc;
}
