ShedLock
========
[![Apache License 2](https://img.shields.io/badge/license-ASF2-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt) [![Build Status](https://travis-ci.org/lukas-krecan/ShedLock.png?branch=master)](https://travis-ci.org/lukas-krecan/ShedLock) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent)

ShedLock makes sure that your scheduled tasks are executed at most once at the same time. 
If a task is being executed on one node, it acquires a lock which prevents execution of the same task from another node (or thread). 
Please note, that **if one task is already being executed on one node, execution on other nodes does not wait, it is simply skipped**.
 
ShedLock uses external store like Mongo, JDBC database, Redis, Hazelcast, ZooKeeper or others for coordination.

Feedback and pull-requests welcome!

#### ShedLock is not a distributed scheduler
Please note that ShedLock is not and will never be full-fledged scheduler, it's just a lock. If you need a distributed scheduler, please use another project.
ShedLock is designed to be used in situations where you have scheduled tasks that are not ready to be executed in parallel, but can be safely
executed repeatedly.

+ [Components](#components)
+ [Usage](#usage)
+ [Lock Providers](#configure-lockprovider)
  - [JdbcTemplate](#jdbctemplate)
  - [Mongo](#mongo)
  - [DynamoDB](#dynamodb)
  - [ZooKeeper (using Curator)](#zookeeper-using-curator)
  - [Redis (using Spring RedisConnectionFactory)](#redis-using-spring-redisconnectionfactory)
  - [Redis (using Jedis)](#redis-using-jedis)
  - [Hazelcast](#hazelcast)
  - [Couchbase](#couchbase)
  - [ElasticSearch](#elasticsearch)
  - [CosmosDB](#cosmosdb)
+ [Duration specification](#duration-specification)
+ [Micronaut integration](#micronaut-integration)
+ [Locking without a framework](#locking-without-a-framework)
+ [Troubleshooting](#troubleshooting)
+ [Modes of Spring integration](#modes-of-spring-integration)
  - [TaskScheduler proxy](#taskscheduler-proxy)
  - [Scheduled method proxy](#scheduled-method-proxy)
+ [Versions](#versions)

## Components
Shedlock consists of three parts
* Core - The locking mechanism
* Integration - integration with your application, using Spring AOP, Micronaut AOP or manual code
* Lock provider - provides the lock using an external process like SQL database, Mongo, Redis and others  

## Usage
To use ShedLock, you do the following
1) Enable and configure Scheduled locking
2) Annotate your scheduled tasks
3) Configure a Lock Provider


### Enable and configure Scheduled locking (Spring)
First of all, we have to import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>4.0.2</version>
</dependency>
```

Now we need to integrate the library with Spring. In order to enable schedule locking use `@EnableSchedulerLock` annotation

```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "30s")
class MySpringConfiguration {
    ...
}
```

### Annotate your scheduled tasks
 
 ```java
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

...

@Scheduled(...)
@SchedulerLock(name = "scheduledTaskName")
public void scheduledTask() {
    // To assert that the lock is held (prevents misconfiguration errors)
    LockAssert.assertLocked();
    // do something
}
```
        
The `@SchedulerLock` annotation has several purposes. First of all, only annotated methods are locked, the library ignores
all other scheduled tasks. You also have to specify the name for the lock. Only one tasks with the same name can be executed
at the same time. 

You can also set `lockAtMostFor` attribute which specifies how long the lock should be kept in case the
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.
**You have to set `lockAtMostFor` to a value which is much longer than normal execution time.** If the task takes longer than
`lockAtMostFor` the resulting behavior may be unpredictable (more then one process will effectively hold the lock).

Lastly, you can set `lockAtLeastFor` attribute which specifies minimum amount of time for which the lock should be kept. 
Its main purpose is to prevent execution from multiple nodes in case of really short tasks and clock difference between the nodes.

#### Example 
Let's say you have a task which you execute every 15 minutes and which usually takes few minutes to run. 
Moreover, you want to execute it at most once per 15 minutes. In such case, you can configure it like this

 ```java
import net.javacrumbs.shedlock.core.SchedulerLock;


@Scheduled(cron = "0 */15 * * * *")
@SchedulerLock(name = "scheduledTaskName", lockAtMostFor = "14m", lockAtLeastFor = "14m")
public void scheduledTask() {
    // do something
}

```
By setting `lockAtMostFor` we make sure that the lock is released even if the node dies and by setting `lockAtLeastFor`
we make sure it's not executed more than once in fifteen minutes. 
Please note that **`lockAtMostFor` is just a safety net for a case that the node executing the task dies, so set it to 
a time that is significantly larger than maximum estimated execution time.**  If the task takes longer than `lockAtMostFor`,
it may be executed again and the results will be unpredictable (more processes will hold the lock).

### Configure LockProvider
There are several implementations of LockProvider.  

#### JdbcTemplate
First, create lock table (**please note that `name` has to be primary key**)

```sql
CREATE TABLE shedlock(
    name VARCHAR(64), 
    lock_until TIMESTAMP(3) NULL, 
    locked_at TIMESTAMP(3) NULL, 
    locked_by  VARCHAR(255), 
    PRIMARY KEY (name)
) 
```
script for MS SQL is [here](https://github.com/lukas-krecan/ShedLock/issues/3#issuecomment-275656227) and for Oracle [here](https://github.com/lukas-krecan/ShedLock/issues/81#issue-355599950)

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>4.0.2</version>
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

Tested with MySql, Postgres and HSQLDB, should work on all other JDBC compliant databases. 

#### Warning
**Do not manually delete lock row or document from DB table.** ShedLock has an in-memory cache of existing locks
so the row will NOT be automatically recreated until application restart. If you need to, you can edit the row/document, risking only
that multiple locks will be held. Since 1.0.0 you can clean the cache by calling `clearCache()` on LockProvider.
 

#### Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo</artifactId>
    <version>4.0.2</version>
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

#### DynamoDB
Import the project

 ```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-dynamodb</artifactId>
    <version>4.0.2</version>
</dependency>
```

Configure:
 
 ```java
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider;

...

@Bean
public LockProvider lockProvider(com.amazonaws.services.dynamodbv2.document.DynamoDB dynamoDB) {
    return new DynamoDBLockProvider(dynamoDB.getTable("existingTableName"));
}
```

> Please note that the lock table must be created externally.
> `DynamoDBUtils#createLockTable` may be used for creating it programmatically.
> A table definition is available from `DynamoDBLockProvider`'s Javadoc.

#### ZooKeeper (using Curator)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-zookeeper-curator</artifactId>
    <version>4.0.2</version>
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
By default, nodes for locks will be created under `/shedlock` node. 

#### Redis (using Spring RedisConnectionFactory)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-spring</artifactId>
    <version>4.0.2</version>
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
Please note that there is a bug #105 when ShedLock is used with spring-data-redis 1 so it's recommended to use either spring-data-redis 2
with newest ShedLock or Jedis provider.


#### Redis (using Jedis)
Import 
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-jedis</artifactId>
    <version>4.0.2</version>
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
    <version>4.0.2/version>
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

#### Couchbase
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-couchbase-javaclient</artifactId>
    <version>4.0.2/version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider;

...

@Bean
public CouchbaseLockProvider lockProvider(Bucket bucket) {
    return new CouchbaseLockProvider(bucket);
}
```

#### Elasticsearch
I am really not sure that it's a good idea to use Elasticsearch as a lock provider. But if you have no other choice, you can. Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-elasticsearch</artifactId>
    <version>4.0.2/version>
</dependency>
```

Configure:

```java
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider;

...

@Bean
public ElasticsearchLockProvider lockProvider(RestHighLevelClient highLevelClient) {
    return new ElasticsearchLockProvider(highLevelClient);
}
```

#### CosmosDB
CosmosDB support is provided by a third-party module available [here](https://github.com/jesty/shedlock-provider-cosmosdb)

## Duration specification
All the annotations where you need to specify a duration support the following formats

* duration+unit - `1s`, `5ms`, `5m`, `1d` (Since 4.0.0)
* duration in ms - `100` (only Spring integration)
* ISO-8601 - `PT15M` (see [Duration.parse()](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-) documentation)   

## Micronaut integration
Since version 4.0.0, it's possible to use Micronaut framework for integration

Import the project:
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-micronaut</artifactId>
    <version>4.0.2</version>
</dependency>
``` 

Configure default lockAtMostFor value (application.yml):
```yaml
shedlock:
  defaults:
    lock-at-most-for: 1m
```

Configure lock provider:
```java
@Singleton
public LockProvider lockProvider() {
    ... select and configure your lock provider
}
``` 

Configure the scheduled task:
```java
@Scheduled(fixedDelay = "1s")
@SchedulerLock(name = "myTask")
public void myTask() {
    assertLocked();
    ...
}
```

## Locking without a framework
It is possible to use ShedLock without a framework

```java
LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

...

Instant lockAtMostUntil = Instant.now().plusSeconds(600);
executor.executeWithLock(runnable, new LockConfiguration("lockName", lockAtMostUntil));

```


## Modes of Spring integration
ShedLock supports two modes of Spring integration.


#### Scheduled Method proxy
Since version 4.0.0, the default mode of Spring integration is an AOP proxy around the annotated method.

The main advantage of this mode, is that it plays well with other frameworks that want to somehow alter the default Spring scheduling mechanism. 
The disadvantage is that the lock is applied even if you call the method directly. Be also aware that only void-returning methods are currently supported, 
an exception is thrown if you annotate and call a method with non-void return type. 

Final and non-public methods are not proxied so either you have to make your scheduled methods public and non-final or use TaskScheduler proxy.  


![Method proxy sequenceDiagram](https://github.com/lukas-krecan/ShedLock/raw/master/documentation/method_proxy.png)  

#### TaskScheduler proxy
This mode wraps Spring `TaskScheduler` in an AOP proxy. The mode is switched-on like this (PROXY_SCHEDULER was the default method before 4.0.0):

```java
@EnableSchedulerLock(interceptMode = PROXY_SCHEDULER)
``` 
 
If you do not specify your task scheduler, a default one is created for you. If you have special needs, just create a bean implementing `TaskScheduler` 
interface and it will get wrapped into the AOP proxy automatically.

```java   
@Bean
public TaskScheduler taskScheduler() {
    return new MySpecialTaskScheduler();
}
```

Alternatively, you can define a bean of type `ScheduledExecutorService` and it will automatically get used by the tasks 
scheduling mechanism.

![TaskScheduler proxy sequence diagram](https://github.com/lukas-krecan/ShedLock/raw/master/documentation/scheduler_proxy.png)

### Spring XML configuration
Spring XML configuration is not supported as of version 3.0.0. If you need it, please use version 2.6.0 or file an issue explaining why it is needed.

## Lock assert
To prevent misconfiguration errors, you can assert that the lock works by using LockAssert:

```java
@Scheduled(...)
@SchedulerLock(..)
public void scheduledTask() {
    // To assert that the lock is held (prevents misconfiguration errors)
    LockAssert.assertLocked();
    // do something
}
```

## Kotlin gotchas
The library is tested with Kotlin and works fine. The only issue is Spring AOP which does not work on final method. If you use `@SchedulerLock` with `@Scheduled`
annotation, everyting should work since Kotling Spring compiler plugin will automatically 'open' the method for you. If `@Scheduled` annotation is not present, you 
have to open the method by yourself. 

## Troubleshooting
Help, ShedLock does not do what it's supposed to do!

1. Check the storage. If you are using JDBC, check the ShedLock table. If it's empty, ShedLock is not properly configured. 
If there is more than one record with the same name, you are missing a primary key.
2. Use ShedLock debug log. ShedLock logs interesting information on DEBUG level with logger name `net.javacrumbs.shedlock`.
It should help you to see what's going on. 
3. For short-running tasks consider using `lockAtLeastFor`. If the tasks are short-running, they can be executed one
after each other, `lockAtLeastFor` can prevent it.
4. If you encounter weird error complaining that a Proxy is not class of `ThreadPoolTaskScheduler` please check https://github.com/lukas-krecan/ShedLock/issues/115 or 
[this StackOverflow quesiton](https://stackoverflow.com/questions/56017382/how-to-fix-websockets-and-shedlock-compatibility-in-spring-boot-application/56036601#56036601) 
 
   

## Requirements and dependencies
* Java 8
* slf4j-api

## Versions
Version 1.x.x is compiled and tested with Spring 5 and Spring Data 2. It should be safe to use ShedLock 1.x.x with Spring 4
if you are not using Spring Redis lock provider which introduced incompatibility. In other words
- If you have dependency on spring-data-redis 2 - use ShedLock 1.x.x
- If you have dependency on spring-data-redis 1 - use ShedLock 0.x.x
- In all other cases, you can use both versions, prefereably 1.x.x


# Change log
## 4.0.2
* Fix NPE caused by Redisson #178
## 4.0.1
* DefaultLockingTaskExecutor made reentrant #175
## 4.0.0
Version 4.0.0 is a major release changing quite a lot stuff
* `net.javacrumbs.shedlock.core.SchedulerLock` has been replaced by `net.javacrumbs.shedlock.spring.annotation.SchedulerLock`. The original annotation has been in wrong module and 
was too complex. Please use the new annotation, the old one still works, but in few years it will be removed.
* Default intercept mode changed from `PROXY_SCHEDULER` to `PROXY_METHOD`. The reason is that there was lot of issues with  `PROXY_SCHEDULER` (for example #168). You can still
use `PROXY_SCHEDULER` mode if you specifay it manually.
* Support for more readable [duration strings](#duration-specification)
* Support for lock assertion `LockAssert.assertLocked()`  
* [Support for Micronaut](#micronaut-integration) added 

## 3.0.1
* Fixed bean definition configuration #171

## 3.0.0
* `EnableSchedulerLock.mode` renamed to `interceptMode`
* Use standard Spring AOP configuration to honor Spring Boot config (supports `proxyTargetClass` flag)
* Removed deprecated SpringLockableTaskSchedulerFactoryBean and related classes
* Removed support for XML configuration

## 2.6.0
* Updated dependency to Spring 2.1.9
* Support for lock extensions (beta)

## 2.5.0
* Zookeeper supports *lockAtMostFor* and *lockAtLeastFor* params
* Better debug logging

## 2.4.0
* Fixed potential deadlock in Hazelcast (thanks @HubertTatar)
* Finding class level annotation in proxy method mode (thanks @volkovs)
* ScheduledLockConfigurationBuilder deprecated

## 2.3.0
* LockProvides is initialized lazilly so it does not change DataSource initialization order

## 2.2.1
* MongoLockProvider accepts MongoCollection as a constructor param

## 2.2.0
* DynamoDBLockProvider added

## 2.1.0
* MongoLockProvider rewritten to use upsert
* ElasticsearchLockProvider added

## 2.0.1
* AOP proxy and annotation configuration support

## 1.3.0
* Can set Timezone to JdbcTemplateLock provider

## 1.2.0
* Support for Couchbase (thanks to @MoranVaisberg)

## 1.1.1
* Spring RedisLockProvider refactored to use RedisTemplate

## 1.1.0
* Support for transaction manager in JdbcTemplateLockProvider (thanks to @grmblfrz)

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
