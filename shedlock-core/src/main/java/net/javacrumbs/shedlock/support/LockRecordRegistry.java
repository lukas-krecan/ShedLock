package net.javacrumbs.shedlock.support;

/**
 * Keeps track of lock records so the lock providers do not have to try insert them over and over.
 */
public interface LockRecordRegistry {
    /**
     * Returns true if the lock record exists.
     */
    boolean lockRecordExists(String name);

    /**
     * Record was successfully inserted.
     */
    void recordInserted(String name);

    /**
     * Record was not inserted.
     */
    void recordNotInserted(String name);

    /**
     * Record was successfully updated
     */
    void recordUpdated(String name);

    /**
     * Record was not updated
     */
    void recordNotUpdated(String name);

    /**
     * Clear the cache
     */
    void clear();

}
