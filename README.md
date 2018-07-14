ShedLock
========
[![Apache License 2](https://img.shields.io/badge/license-ASF2-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt) [![Build Status](https://travis-ci.org/lukas-krecan/ShedLock.png?branch=master)](https://travis-ci.org/lukas-krecan/ShedLock) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent)

ShedLock does one and only one thing. It makes sure your scheduled tasks are executed at most once at the same time. 
If a task is being executed on one node, it acquires a lock which prevents execution of the same task from another node (or thread). 
Please note, that **if one task is already being executed on one node, execution on other nodes does not wait, it is simply skipped**.
 
Currently, Spring scheduled tasks coordinated through Mongo, JDBC database, Redis, Hazelcast or ZooKeeper are supported. More
scheduling and coordination mechanisms and expected in the future.

Feedback and pull-requests welcome!

#### ShedLock is not a distributed scheduler
Please note that ShedLock is not and will never be full-fledged scheduler, it's just a lock. If you need a distributed scheduler, please use another project.
ShedLock is designed to be used in situations where you have scheduled tasks that are not ready to be executed in parallel, but can be safely
executed repeatedly. For example if the task is fetching records from a database, processing them and marking them as processed at the end without
using any transaction. In such case ShedLock may be right for you.


+ [Usage](#usage)
+ [Lock Providers](#configure-lockprovider)
  - [Mongo](#mongo)
  - [JdbcTemplate](#jdbctemplate)
  - [Plain JDBC](#plain-jdbc)
  - [Warning](#warning)
  - [ZooKeeper (using Curator)](#zookeeper--using-curator-)
  - [Redis (using Spring RedisConnectionFactory)](#redis--using-spring-redisconnectionfactory-)
  - [Redis (using Jedis)](#redis--using-jedis-)
  - [Hazelcast](#hazelcast)
+ [Spring XML configuration](#spring-xml-configuration)
+ [Running without Spring](#running-without-spring)
+ [Versions](#versions)

## Usage
### Requirements and dependencies
* Java 8
* slf4j-api
* Spring Framework (optional)


### Import project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>1.0.0</version>
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
at the same time. 

You can also set `lockAtMostFor` attribute which specifies how long the lock should be kept in case the
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.

Lastly, you can set `lockAtLeastFor` attribute which specifies minimum amount of time for which the lock should be kept. 
Its main purpose is to prevent execution from multiple nodes in case of really short tasks and clock difference between the nodes.

#### Example 
Let's say you have a task which you execute every 15 minutes and which usually takes few minutes to run. 
Moreover, you want to execute it at most once per 15 minutes. In such case, you can configure it like this

 ```java
import net.javacrumbs.shedlock.core.SchedulerLock;

...
private static final int FOURTEEN_MIN = 14 * 60 * 1000;
...

@Scheduled(cron = "0 */15 * * * *")
@SchedulerLock(name = "scheduledTaskName", lockAtMostFor = FOURTEEN_MIN, lockAtLeastFor = FOURTEEN_MIN)
public void scheduledTask() {
    // do something
}

```
By setting `lockAtMostFor` we make sure that the lock is released even if the node dies and by setting `lockAtLeastFor`
we make sure it's not executed more than once in fifteen minutes. 
Please note that **`lockAtMostFor` is just a safety net for a case that the node executing the task dies, so set it to 
a time that is significantly larger than maximum estimated execution time.**  If the task takes longer than `lockAtMostFor`,
it will be executed again.


### Configure the task scheduler
Now we need to integrate the library into Spring. It's done by wrapping standard Spring task scheduler.  

```java
import net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactory;

...
@Bean
public ScheduledLockConfiguration taskScheduler(LockProvider lockProvider) {
    return ScheduledLockConfigurationBuilder
        .withLockProvider(lockProvider)
        .withPoolSize(10)
        .withDefaultLockAtMostFor(Duration.ofMinutes(10))
        .build();
}
```

Or if you already have an instance of ScheduledExecutorService

```java
@Bean
public ScheduledLockConfiguration taskScheduler(ScheduledExecutorService executorService, LockProvider lockProvider) {
    return ScheduledLockConfigurationBuilder
        .withLockProvider(lockProvider)
        .withExecutorService(executorService)
        .withDefaultLockAtMostFor(Duration.ofMinutes(10))
        .build();
}
```

### Configure LockProvider
There are several implementations of LockProvider.  

#### Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo</artifactId>
    <version>1.0.0</version>
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
    <version>1.0.0</version>
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
    <version>1.0.0</version>
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


#### Warning
**Do not manually delete lock row or document from DB table or Mongo collection.** ShedLock has an in-memory cache of existing locks
so the row will NOT be automatically recreated until application restart. If you need to, you can edit the row/document, risking only
that multiple locks will be held. Since 1.0.0 you can clean the cache by calling `clearCache()` on LockProvider.
 
#### ZooKeeper (using Curator)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-zookeeper-curator</artifactId>
    <version>1.0.0</version>
</dependency>
```

and configure

```java
import net.javacrumbs.shedlock.provider.zookeeper.curator.ZookeeperCuratorLockProvider;

...

@Bean
public LockProvider lockProvider(org.apache.curator.framework.CuratorFramework client) {
    return new ZookeeperCuratorLockProvider(client);
}
```
By default, ephemeral nodes for locks will be created under `/shedlock` node. 

#### Redis (using Spring RedisConnectionFactory)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-spring</artifactId>
    <version>1.0.0</version>
</dependency>
```

and configure

```java
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;

...

@Bean
public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
    return new RedisLockProvider(connectionFactory, ENV);
}
```

If you have dependency on spring-data-redis 2 use ShedLock 1.x.x, if you have dependency on spring-data-redis 1 use ShedLock 0.x.x.


#### Redis (using Jedis)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-jedis</artifactId>
    <version>1.0.0</version>
</dependency>
```

and configure

```java
import net.javacrumbs.shedlock.provider.redis.jedis.JedisLockProvider;

...

@Bean
public LockProvider lockProvider(JedisPool jedisPool) {
    return new JedisLockProvider(jedisPool, ENV);
}
```

#### Hazelcast
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-hazelcast</artifactId>
    <version>1.0.0/version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.hazelcast.HazelcastLockProvider;

...

@Bean
public HazelcastLockProvider lockProvider(HazelcastInstance hazelcastInstance) {
    return new HazelcastLockProvider(hazelcastInstance);
}
```

### Spring XML configuration

If you are using Spring XML config, use this configuration

```xml
<!-- lock provider of your choice (jdbc/zookeeper/mongo/whatever) -->
<bean id="lockProvider" class="net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider">
    <constructor-arg ref="dataSource"/>
</bean>

<bean id="scheduler" class="net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactoryBean">
    <constructor-arg>
        <task:scheduler id="sch" pool-size="10"/>
    </constructor-arg>
    <constructor-arg ref="lockProvider"/>
    <constructor-arg name="defaultLockAtMostFor">
        <bean class="java.time.Duration" factory-method="ofMinutes">
            <constructor-arg value="10"/>
        </bean>
    </constructor-arg>
</bean>


<!-- Your task(s) without change (or annotated with @Scheduled)-->
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

### Running without Spring
It is possible to use ShedLock without Spring

```java
LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

...

Instant lockAtMostUntil = Instant.now().plusSeconds(600);
executor.executeWithLock(runnable, new LockConfiguration("lockName", lockAtMostUntil));

```

## Versions
Version 1.x.x is compiled and tested with Spring 5 and Spring Data 2. It should be safe to use ShedLock 1.x.x with Spring 4
if you are not using Spring Redis lock provider which introduced incompatibility. In other words
- If you have dependency on spring-data-redis 2 - use ShedLock 1.x.x
- If you have dependency on spring-data-redis 1 - use ShedLock 0.x.x
- In all other cases, you can use both versions, prefereably 1.x.x


## Change log
## 1.0.0
* Upgraded dependencies to Spring 5 and Spring Data 2
* Removed deprecated net.javacrumbs.shedlock.provider.jedis.JedisLockProvider (use  net.javacrumbs.shedlock.provider.redis.jedis.JedisLockProvide instead)
* Removed deprecated SpringLockableTaskSchedulerFactory (use ScheduledLockConfigurationBuilder instead)

## 0.18.2
* ablility to clean lock cache

## 0.18.1
* shedlock-provider-redis-spring made compatible with spring-data-redis 1.x.x  

## 0.18.0
* Added shedlock-provider-redis-spring (thanks to @siposr)
* shedlock-provider-jedis moved to shedlock-provider-redis-jedis

## 0.17.0
* Support for SPEL in lock name annotation 

## 0.16.1
* Automatically closing TaskExecutor on Spring shutdown

## 0.16.0
* Removed spring-test from shedlock-spring compile time dependencies
* Added Automatic-Module-Names

## 0.15.1
* Hazelcast works with remote cluster 

## 0.15.0
* Fixed ScheduledLockConfigurationBuilder interfaces #32
* Hazelcast code refactoring

## 0.14.0
* Support for Hazelcast (thanks to @peyo)

## 0.13.0
* Jedis constructor made more generic (thanks to @mgrzeszczak) 

## 0.12.0
* Support for property placeholders in annotation lockAtMostForString/lockAtLeastForString
* Support for composed annotations
* ScheduledLockConfigurationBuilder introduced (deprecating SpringLockableTaskSchedulerFactory)

## 0.11.0
* Support for Redis (thanks to @clamey)
* Checking that lockAtMostFor is in the future
* Checking that lockAtMostFor is larger than lockAtLeastFor


## 0.10.0
* jdbc-template-provider does not participate in task transaction 

## 0.9.0
* Support for @SchedulerLock annotations on proxied classes  

## 0.8.0
* LockableTaskScheduler made AutoClosable so it's closed upon Spring shutdown

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
