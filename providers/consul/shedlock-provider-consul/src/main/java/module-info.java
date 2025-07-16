/**
 * ShedLock Consul provider module.
 *
 * This module provides HashiCorp Consul-based lock provider implementation
 * using the Consul Java client.
 */
module net.javacrumbs.shedlock.provider.consul {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires org.slf4j;
    requires consul.api;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.consul;
}
