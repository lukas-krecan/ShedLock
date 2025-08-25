/**
 * ShedLock R2DBC provider module.
 *
 * This module provides R2DBC-based lock provider implementation
 * using reactive database connectivity.
 */
module net.javacrumbs.shedlock.provider.r2dbc {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires r2dbc.spi;
    requires reactor.core;
    requires org.reactivestreams;
    requires org.slf4j;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.r2dbc;
}
