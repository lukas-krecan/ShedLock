/**
 * ShedLock CDI integration module.
 *
 * This module provides CDI (Contexts and Dependency Injection) integration
 * for ShedLock, including interceptors and annotations for method-level locking.
 */
module net.javacrumbs.shedlock.cdi {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires jakarta.cdi;
    requires jakarta.annotation;
    requires microprofile.config.api;

    // Export CDI integration packages
    exports net.javacrumbs.shedlock.cdi;
}
