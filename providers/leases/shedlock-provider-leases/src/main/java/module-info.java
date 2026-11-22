/**
 * ShedLock Kubernetes Leases provider module.
 *
 * This module provides a lock provider implementation based on Kubernetes
 * coordination.k8s.io Lease resources.
 */
module net.javacrumbs.shedlock.provider.leases {
    requires net.javacrumbs.shedlock.core;
    requires kubernetes.client.api;
    requires kubernetes.model.core;
    requires kubernetes.model.coordination;
    requires org.slf4j;
    requires static org.jspecify;

    exports net.javacrumbs.shedlock.provider.leases;
}
