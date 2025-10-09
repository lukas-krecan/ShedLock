/**
 * ShedLock MongoDB provider module.
 *
 * This module provides MongoDB-based lock provider implementation
 * using the MongoDB Java driver.
 */
module net.javacrumbs.shedlock.provider.mongo {
    requires net.javacrumbs.shedlock.core;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.bson;
    requires org.mongodb.driver.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.mongo;
}
