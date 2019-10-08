function checkLockAndAcquire(id, lockUntil, now, lockedBy, lockGroup) {
    var context = getContext();
    var container = context.getCollection();
    var response = context.getResponse();

    var filterQuery =
    {
        'query' : 'SELECT * FROM locks s where s.lockGroup = @lockGroup and s.id = @lockName and s.lockUntil >= @now',
        'parameters' : [{'name': '@lockName', 'value': id}, {'name': '@now', 'value': parseInt(now)}, {'name': '@lockGroup', 'value': lockGroup}]
    };

    var accept = container.queryDocuments(container.getSelfLink(), filterQuery, {},
        function (err, items, responseOptions) {
            if (err) throw new Error("Error" + err.message);

             // There are three possible situations:
            // 1. The lock document does not exist yet, we have the lock
            // 2. The lock document exists and lockUntil <= now, we have the lock
            // 3. The lock document exists and lockUntil > now, cannot acquire the lock

            var hasLock = items.length > 0;

            if(!hasLock){
                console.log("Acquiring lock!")
                container.upsertDocument(container.getSelfLink(), {"id": id, "lockUntil": parseInt(lockUntil), "lockedAt": parseInt(now), "lockedBy": lockedBy, "lockGroup": lockGroup});
            } else {
                console.log("Cannot acquire lock!")
            }

            response.setBody(hasLock); ;
        });
}