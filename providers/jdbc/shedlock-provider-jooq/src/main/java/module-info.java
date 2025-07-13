/**
 * ShedLock JOOQ provider module.
 *
 * This module provides JOOQ-based lock provider implementation
 * using the JOOQ SQL library.
 */
module net.javacrumbs.shedlock.provider.jooq {
    requires java.base;
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires org.jooq;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.jooq;
}
