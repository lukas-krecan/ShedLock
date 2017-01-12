ShedLock [![Build Status](https://travis-ci.org/lukas-krecan/ShedLock.png?branch=master)](https://travis-ci.org/lukas-krecan/ShedLock) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent)
========

You have a simple Spring scheduler which works great until you need to run the application
on multiple instances. Now what? You want to execute your tasks only once. You can use Quartz, 
but it's incredibly complex. You can use environment variable to determine a "scheduler master",
but what if it dies? Or you can use ShedLock.

ShedLock does one and only thing. It makes sure your tasks ar executed at most once. It coordinates
cluster nodes using shared database. If a task is being executed on one node, it acquires a lock which
prevents execution of the same task from another node (or thread). Please note, that **if one task is already running
execution from another node does not wait, it is simply skipped**.
 
Currently, only Spring scheduled tasks coordinated through Mongo or JDBC database are supported. More
scheduling and coordination mechanisms and expected in the future.

Feedback and pull-requests welcome!

## Usage
### Import project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>0.4.0</version>
</dependency>
```

### Annotate your scheduled tasks
 
 ```java
import net.javacrumbs.shedlock.core.SchedulerLock;

...

@Scheduled(...)
@SchedulerLock(name = "scheduledTaskName")
public void scheduledTask() {
    // do something
}
```
        
The `@SchedulerLock` annotation has several purposes. First of all, only annotated methods are locked, the library ignores
all other scheduled tasks. You also have to specify the name for the lock. Only one tasks with the same name can be executed
at the same time. Lastly, you can set `lockAtMostFor` attribute which specifies how long the lock should be kept in case the
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.

### Configure the task scheduler
Now we need to integrate the library into Spring. It's done by wrapping standard Spring task scheduler.  

```java
import net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactory;

...

@Bean
public TaskScheduler taskScheduler(LockProvider lockProvider) {
    int poolSize = 10;
    return SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(poolSize, lockProvider);
}
```

Or if you already have an instance of ScheduledExecutorService

```java
@Bean
public TaskScheduler taskScheduler(ScheduledExecutorService executorService, LockProvider lockProvider) {
    return SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(new ConcurrentTaskScheduler(executorService), lockProvider);
}
```

### Configure LockProvider
#### Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo</artifactId>
    <version>0.4.0</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;

...

@Bean
public LockProvider lockProvider(MongoClient mongo) {
    return new MongoLockProvider(mongo, "databaseName");
}
```

Please note that MongoDB integration requires Mongo >= 2.4 and mongo-java-driver >= 3.4.0


#### JdbcTemplate

Create the table

```sql
CREATE TABLE shedlock(
    name VARCHAR(64), 
    lock_until TIMESTAMP(3) NULL, 
    locked_at TIMESTAMP(3) NULL, 
    locked_by  VARCHAR(255), 
    PRIMARY KEY (name)
) 
```

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>0.4.0</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

...

@Bean
public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcTemplateLockProvider(dataSource);
}
```

Tested with MySql, Postgres and HSQLDB

#### Plain JDBC
For those who do not want to use jdbc-template, there is plain JDBC lock provider. Just import 

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc</artifactId>
    <version>0.4.0</version>
</dependency>
```

and configure

```java
import net.javacrumbs.shedlock.provider.jdbc.JdbcLockProvider;

...

@Bean
public LockProvider lockProvider(DataSource dataSource) {
    return new JdbcLockProvider(dataSource);
}
```
the rest is the same as with JdbcTemplate lock provider. 

### Spring XML configuration

If you are using Spring XML config, use this configuration

```xml
<!-- Wrap the original scheduler -->
<bean id="scheduler" class="net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactory" factory-method="newLockableTaskScheduler">
    <constructor-arg>
        <!-- The original scheduler -->
        <task:scheduler id="sch" pool-size="10"/>
    </constructor-arg>
    <constructor-arg ref="lockProvider"/>
</bean>


<!-- Your task(s) without change -->
<task:scheduled-tasks scheduler="scheduler">
    <task:scheduled ref="task" method="run" fixed-delay="1" fixed-rate="1"/>
</task:scheduled-tasks>
```

Annotate scheduler method(s)


```java
@SchedulerLock(name = "taskName")
public void run() {

}
```

## Requirements and dependencies
1. Java 8
2. slf4j-api


##Change log
## 0.4.0
1. Extracted LockingTaskExecutor
2. LockManager.executeIfNotLocked renamed to executeWithLock
3. Default table name in JDBC lock providers

## 0.3.0
1. `@ShedlulerLock.name` made obligatory
2. `@ShedlulerLock.lockForMillis` renamed to lockAtMostFor
3. Adding plain JDBC LockProvider
4. Adding ZooKeepr LockProvider
