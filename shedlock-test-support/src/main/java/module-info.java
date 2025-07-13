/**
 * ShedLock test support module providing utilities for testing lock providers.
 *
 * This module contains test utilities and base classes for testing
 * ShedLock implementations.
 */
module net.javacrumbs.shedlock.test.support {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.slf4j;

    // Export test support packages
    exports net.javacrumbs.shedlock.test.support;
}
