/**
 * ShedLock etcd provider module.
 *
 * This module provides etcd-based lock provider implementation
 * using the jetcd client library.
 */
module net.javacrumbs.shedlock.provider.etcd.jetcd {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires jetcd.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.etcd.jetcd;
}
