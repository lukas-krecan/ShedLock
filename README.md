ShedLock [![Build Status](https://travis-ci.org/lukas-krecan/ShedLock.png?branch=master)](https://travis-ci.org/lukas-krecan/ShedLock) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent)
========

You have a simple Spring scheduler which works great until you need to run the application
on multiple instances. Now what? You want to execute your tasks only once. You can use Quartz, 
but it's incredibly complex. You can use environment variable to determine a "scheduler master",
but what if it dies? Or you can use ShedLock.

ShedLock does one and only thing. It makes sure your tasks ar executed at most once. It coordinates
cluster nodes using shared database. If a task is being executed on one node, it acquires a lock which
prevents execution of the same task from another node (or thread). Please note, that **if one task is already running
the other one is does not wait, it is simply skipped**.
 
Since this is the very first version, only Spring annotated tasks coordinated through Mongo or JDBC database are supported. More
scheduling and coordination mechanisms and expected in the future. 

## Usage
### Import project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Annotate your scheduled tasks
 
 ```java
@Scheduled( ... )
@SchedulerLock(name = "scheduledTaskName")
public void scheduledTask() {
    // do something
}
```
        
The `@SchedulerLock` annotation has several purposes. First of all, only annotated methods are locked, the library ignores
all other scheduled tasks. You also have to specify the name for the lock. Only one tasks with the same name can be executed
at the same time. Lastly, you can set `lockForMillis` attribute which specifies how long the lock should be kept in case the
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.

### Configure the task scheduler
Now we need to integrate the library into Spring. It's done by wrapping standard Spring task scheduler.  

```java
@Bean
public TaskScheduler taskScheduler(LockProvider lockProvider) {
    int poolSize = 10;
    return SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(poolSize, lockProvider);
}
```

### Configure LockProvider
#### Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo</artifactId>
    <version>0.2.0</version>
</dependency>
```

Configure:

```java
@Bean
public LockProvider lockProvider(MongoClient mongo) {
    return new MongoLockProvider(mongo, "databaseName");
}
```

Please note that MongoDB integration requires Mongo >= 3.2 and mongo-java-driver >= 3.4.0


#### JdbcTemplate

Create the table

```sql
CREATE TABLE shedlock(name VARCHAR(64), lock_until TIMESTAMP, locked_at TIMESTAMP, locked_by  VARCHAR(255), PRIMARY KEY (name))
```

Configure:

```java
@Bean
public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(dataSource, "shedlock");
}
```

Tested with MySql, Postgres and HSQLDB