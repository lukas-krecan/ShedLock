/**
 * ShedLock ArangoDB provider module.
 *
 * This module provides ArangoDB-based lock provider implementation
 * using the ArangoDB Java driver.
 */
module net.javacrumbs.shedlock.provider.arangodb {
    requires net.javacrumbs.shedlock.core;
    requires com.arangodb.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.arangodb;
}
