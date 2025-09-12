/**
 * ShedLock Memcached Spy provider module.
 *
 * This module provides Memcached-based lock provider implementation
 * using the Spy Memcached client library.
 */
module net.javacrumbs.shedlock.provider.memcached.spy {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires spymemcached;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.memcached.spy;
}
