/**
 * ShedLock Valkey GLIDE provider module.
 *
 * <p>This module provides Valkey/Redis-based lock provider implementation using the Valkey GLIDE
 * client library.
 */
@SuppressWarnings("module")
module net.javacrumbs.shedlock.provider.valkey.glide {
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.redis.support;
    requires valkey.glide;

    exports net.javacrumbs.shedlock.provider.valkey.glide;

    // Nested JUnit tests live in the same package and need reflective access
    opens net.javacrumbs.shedlock.provider.valkey.glide to
            org.junit.platform.commons;
}
