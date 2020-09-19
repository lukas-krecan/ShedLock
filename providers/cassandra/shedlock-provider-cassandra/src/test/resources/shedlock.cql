CREATE KEYSPACE IF NOT EXISTS shedlock WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1  };
CREATE TABLE shedlock.lock (name text PRIMARY KEY, lockUntil timestamp, lockedAt timestamp, lockedBy text);
