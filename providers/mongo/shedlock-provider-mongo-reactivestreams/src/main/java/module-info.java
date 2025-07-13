/**
 * ShedLock MongoDB Reactive Streams provider module.
 *
 * This module provides MongoDB-based lock provider implementation
 * using MongoDB Reactive Streams driver.
 */
module net.javacrumbs.shedlock.provider.mongo.reactivestreams {
    requires java.base;
    requires net.javacrumbs.shedlock.core;
    requires org.mongodb.driver.reactivestreams;
    requires org.mongodb.bson;
    requires org.reactivestreams;
    requires org.mongodb.driver.core;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.mongo.reactivestreams;
}
