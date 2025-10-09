/**
 * ShedLock test support module providing utilities for testing lock providers.
 *
 * This module contains test utilities and base classes for testing
 * ShedLock implementations.
 */
module net.javacrumbs.shedlock.test.support {
    requires net.javacrumbs.shedlock.core;
    requires org.assertj.core;
    requires org.junit.jupiter.api;
    requires org.slf4j;
    requires static org.jspecify;

    // Export test support packages
    exports net.javacrumbs.shedlock.test.support;
}
