/**
 * ShedLock Neo4j provider module.
 *
 * This module provides Neo4j-based lock provider implementation
 * using the Neo4j Java driver.
 */
module net.javacrumbs.shedlock.provider.neo4j {
    requires net.javacrumbs.shedlock.core;
    requires org.neo4j.driver;

    // Export provider packages
    exports net.javacrumbs.shedlock.provider.neo4j;
}
