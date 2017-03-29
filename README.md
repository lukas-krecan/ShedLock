ShedLock [![Build Status](https://travis-ci.org/lukas-krecan/ShedLock.png?branch=master)](https://travis-ci.org/lukas-krecan/ShedLock) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent)
========

ShedLock does one and only thing. It makes sure your scheduled tasks ar executed at most once at the same time. 
If a task is being executed on one node, it acquires a lock which prevents execution of the same task from another node (or thread). 
Please note, that **if one task is already being executed on one node, execution on other nodes does not wait, it is simply skipped**.
Moreover, if task on one node finishes, it may be executed again (for example due to clock difference between nodes).
 
Currently, only Spring scheduled tasks coordinated through Mongo, JDBC database or ZooKeeper are supported. More
scheduling and coordination mechanisms and expected in the future.

Feedback and pull-requests welcome!

## ShedLock is not a distributed scheduler
Please note that ShedLock is not and will never be full-fledged scheduler, it's just a lock. If you need a distributed scheduler, please use another project.
ShedLock is designed to be used in situations where you have scheduled tasks that are not ready to be executed in parallel, but can be safely
executed repeatedly. For example if the task is fetching records from a database, processing them and marking them as processed at the end without
using transaction. In such case you can execute the task as many times you want as long as they are not executed in parallel.


## Usage
### Import project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>0.7.0</version>
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
    return SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(executorService, lockProvider, Duration.of(10, MINUTES));
}
```

### Configure LockProvider
#### Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo</artifactId>
    <version>0.7.0</version>
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
script for MS SQL is [here](https://github.com/lukas-krecan/ShedLock/issues/3#issuecomment-275656227)

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>0.7.0</version>
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
    <version>0.7.0</version>
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
 
#### ZooKeeper (using Curator)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-zookeeper-curator</artifactId>
    <version>0.7.0</version>
</dependency>
```

and configure

```java
@Bean
public LockProvider lockProvider(org.apache.curator.framework.CuratorFramework client) {
    return new ZookeeperCuratorLockProvider(client);
}
```
By default, ephemeral nodes for locks will be created under `/shedlock` node. 

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

## 0.7.0
* Support for lockAtLeastFor

## 0.6.0
* Possible to configure defaultLockFor time so it does not have to be repeated in every annotation

## 0.5.0
* ZooKeeper nodes created under /shedlock by default

## 0.4.1
* JdbcLockProvider insert does not fail on DataIntegrityViolationException

## 0.4.0
* Extracted LockingTaskExecutor
* LockManager.executeIfNotLocked renamed to executeWithLock
* Default table name in JDBC lock providers

## 0.3.0
* `@ShedlulerLock.name` made obligatory
* `@ShedlulerLock.lockForMillis` renamed to lockAtMostFor
* Adding plain JDBC LockProvider
* Adding ZooKeepr LockProvider
