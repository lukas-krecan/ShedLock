# Distributed lock using CosmosDB on Azure.

It uses a collection that contains documents like this:

    {
       "_id" : "lock name",
       "lockUntil" : ISODate("2017-01-07T16:52:04.071Z"),
       "lockedAt" : ISODate("2017-01-07T16:52:03.932Z"),
       "lockedBy" : "host name"
    }

lockedAt and lockedBy are just for troubleshooting and are not read by the code.

This implementation is inspired to the MongoDB implementation.

* Attempts to insert a new lock record. As an optimization, we keep in-memory track of created lock records. If the record
has been inserted, returns lock.
* We will invoke a stored procedure (checkLockAndAcquire.js) to check if the locke exists and update table.
* If the stored procedure returns false, it means that there isn't a lock, so, we have the lock. Otherwise somebody else holds the lock
* When unlocking, lockUntil is set to now.

ATTENTION: The [integration test](src/test/java/net/javacrumbs/shedlock/provider/cosmosdb/CosmosDbProviderIntegrationTest.java) is ignored (annotated with @Ignore) because you need a CosmosDB instance on Azure, or the [CosmosDB local emulator](https://docs.microsoft.com/azure/cosmos-db/local-emulator).
The instance parameter must be set in con [config.properties](src/test/resources/config.properties).
When you create the collection you need to create the stored procedure [checkLockAndAcquire.js](storedprocedures/checkLockAndAcquire.js).