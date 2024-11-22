ShedLock
========
[![Apache License 2](https://img.shields.io/badge/license-ASF2-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0.txt) [![Build Status](https://github.com/lukas-krecan/ShedLock/workflows/CI/badge.svg)](https://github.com/lukas-krecan/ShedLock/actions) [![Maven Central](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent/badge.svg)](https://maven-badges.herokuapp.com/maven-central/net.javacrumbs.shedlock/shedlock-parent)

ShedLock makes sure that your scheduled tasks are executed at most once at the same time.
If a task is being executed on one node, it acquires a lock which prevents execution of the same task from another node (or thread).
Please note, that **if one task is already being executed on one node, execution on other nodes does not wait, it is simply skipped**.

ShedLock uses an external store like Mongo, JDBC database, Redis, Hazelcast, ZooKeeper or others for coordination.

Feedback and pull-requests welcome!

#### ShedLock is not a distributed scheduler
Please note that ShedLock is not and will never be full-fledged scheduler, it's just a lock. If you need a distributed
scheduler, please use another project ([db-scheduler](https://github.com/kagkarlsson/db-scheduler), [JobRunr](https://www.jobrunr.io/en/)).
ShedLock is designed to be used in situations where you have scheduled tasks that are not ready to be executed in parallel, but can be safely
executed repeatedly. Moreover, the locks are time-based and ShedLock assumes that clocks on the nodes are synchronized.

+ [Versions](#versions)
+ [Components](#components)
+ [Usage](#usage)
+ [Lock Providers](#configure-lockprovider)
  - [JdbcTemplate](#jdbctemplate)
  - [R2DBC](#r2dbc)
  - [jOOQ](#jooq-lock-provider)
  - [Micronaut Data Jdbc](#micronaut-data-jdbc)
  - [Mongo](#mongo)
  - [DynamoDB 2](#dynamodb-2)
  - [ZooKeeper (using Curator)](#zookeeper-using-curator)
  - [Redis (using Spring RedisConnectionFactory)](#redis-using-spring-redisconnectionfactory)
  - [Redis (using Spring ReactiveRedisConnectionFactory)](#redis-using-spring-reactiveredisconnectionfactory)
  - [Redis (using Jedis)](#redis-using-jedis)
  - [Hazelcast](#hazelcast)
  - [Couchbase](#couchbase)
  - [ElasticSearch](#elasticsearch)
  - [OpenSearch](#opensearch)
  - [CosmosDB](#cosmosdb)
  - [Cassandra](#cassandra)
  - [Consul](#consul)
  - [ArangoDB](#arangodb)
  - [Neo4j](#neo4j)
  - [Etcd](#etcd)
  - [Apache Ignite](#apache-ignite)
  - [In-Memory](#in-memory)
  - [Memcached](#memcached-using-spymemcached)
  - [Datastore](#datastore)
+ [Multi-tenancy](#multi-tenancy)
+ [Customization](#customization)
+ [Duration specification](#duration-specification)
+ [Extending the lock](#extending-the-lock)
+ [Micronaut integration](#micronaut-integration)
+ [CDI integration](#cdi-integration)
+ [Locking without a framework](#locking-without-a-framework)
+ [Troubleshooting](#troubleshooting)
+ [Modes of Spring integration](#modes-of-spring-integration)
  - [Scheduled method proxy](#scheduled-method-proxy)
  - [TaskScheduler proxy](#taskscheduler-proxy)
+ [Release notes](#release-notes)

## Versions
If you are using JDK >17 and up-to-date libraries like Spring 6, use version **5.1.0** ([Release Notes](#500-2022-12-10)). If you
are on older JDK or library, use version **4.44.0** ([documentation](https://github.com/lukas-krecan/ShedLock/tree/version4)).

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
    <version>6.0.1</version>
</dependency>
```

Now we need to integrate the library with Spring. In order to enable schedule locking use `@EnableSchedulerLock` annotation

```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "10m")
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
all other scheduled tasks. You also have to specify the name for the lock. Only one task with the same name can be executed
at the same time.

You can also set `lockAtMostFor` attribute which specifies how long the lock should be kept in case the
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes
(unless `lockAtLeastFor` is specified, see below)
**You have to set `lockAtMostFor` to a value which is much longer than normal execution time.** If the task takes longer than
`lockAtMostFor` the resulting behavior may be unpredictable (more than one process will effectively hold the lock).

If you do not specify `lockAtMostFor` in `@SchedulerLock` default value from `@EnableSchedulerLock` will be used.

Lastly, you can set `lockAtLeastFor` attribute which specifies minimum amount of time for which the lock should be kept.
Its main purpose is to prevent execution from multiple nodes in case of really short tasks and clock difference between the nodes.

All the annotations support Spring Expression Language (SpEL).

#### Example
Let's say you have a task which you execute every 15 minutes and which usually takes few minutes to run.
Moreover, you want to execute it at most once per 15 minutes. In that case, you can configure it like this:

```java
import net.javacrumbs.shedlock.core.SchedulerLock;


@Scheduled(cron = "0 */15 * * * *")
@SchedulerLock(name = "scheduledTaskName", lockAtMostFor = "14m", lockAtLeastFor = "14m")
public void scheduledTask() {
    // do something
}

```
By setting `lockAtMostFor` we make sure that the lock is released even if the node dies. By setting `lockAtLeastFor`
we make sure it's not executed more than once in fifteen minutes.
Please note that **`lockAtMostFor` is just a safety net in case that the node executing the task dies, so set it to
a time that is significantly larger than maximum estimated execution time.**  If the task takes longer than `lockAtMostFor`,
it may be executed again and the results will be unpredictable (more processes will hold the lock).

### Configure LockProvider
There are several implementations of LockProvider.

#### JdbcTemplate
First, create lock table (**please note that `name` has to be primary key**)

```sql
# MySQL, MariaDB
CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3), locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

# Postgres
CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

# Oracle
CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until TIMESTAMP(3) NOT NULL,
    locked_at TIMESTAMP(3) NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

# MS SQL
CREATE TABLE shedlock(name VARCHAR(64) NOT NULL, lock_until datetime2 NOT NULL,
    locked_at datetime2 NOT NULL, locked_by VARCHAR(255) NOT NULL, PRIMARY KEY (name));

# DB2
CREATE TABLE shedlock(name VARCHAR(64) NOT NULL PRIMARY KEY, lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL, locked_by VARCHAR(255) NOT NULL);
```

Or use [this](micronaut/test/micronaut4-jdbc/src/main/resources/db/liquibase-changelog.xml) liquibase change-set.

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

...
@Bean
public LockProvider lockProvider(DataSource dataSource) {
            return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Works on Postgres, MySQL, MariaDb, MS SQL, Oracle, DB2, HSQL and H2
                .build()
            );
}
```
By specifying `usingDbTime()` the lock provider will use UTC time based on the DB server clock.
If you do not specify this option, clock from the app server will be used (the clocks on app servers may not be
synchronized thus leading to various locking issues).

It's strongly recommended to use `usingDbTime()` option as it uses DB engine specific SQL that prevents INSERT conflicts.
See more details [here](https://stackoverflow.com/a/76774461/277042).

For more fine-grained configuration use other options of the `Configuration` object

```java
new JdbcTemplateLockProvider(builder()
    .withTableName("shdlck")
    .withColumnNames(new ColumnNames("n", "lck_untl", "lckd_at", "lckd_by"))
    .withJdbcTemplate(new JdbcTemplate(getDatasource()))
    .withLockedByValue("my-value")
    .withDbUpperCase(true)
    .build())
```

If you need to specify a schema, you can set it in the table name using the usual dot notation
`new JdbcTemplateLockProvider(datasource, "my_schema.shedlock")`

To use a database with case-sensitive table and column names, the `.withDbUpperCase(true)` flag can be used.
Default is `false` (lowercase).


#### Warning
**Do not manually delete lock row from the DB table.** ShedLock has an in-memory cache of existing lock rows
so the row will NOT be automatically recreated until application restart. If you need to, you can edit the row/document, risking only
that multiple locks will be held.

#### R2DBC
If you are really brave, you can try experimental R2DBC support. Please keep in mind that the
capabilities of this lock provider are really limited and that the whole ecosystem around R2DBC
is in flux and may easily break.

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-r2dbc</artifactId>
    <version>6.0.1</version>
</dependency>
```

and use it.

```java
@Override
protected LockProvider getLockProvider() {
    return new R2dbcLockProvider(connectionFactory);
}
```
I recommend using [R2DBC connection pool](https://github.com/r2dbc/r2dbc-pool).

#### jOOQ lock provider
First, create lock table as described in the [JdbcTemplate](#jdbctemplate) section above.

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jooq</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.jooq;

...
@Bean
public LockProvider getLockProvider(DSLContext dslContext) {
    return new JooqLockProvider(dslContext);
}
```

jOOQ provider has a bit different transactional behavior. While the other JDBC lock providers
create new transaction (with REQUIRES_NEW), jOOQ [does not support setting it](https://github.com/jOOQ/jOOQ/issues/4836).
ShedLock tries to create a new transaction, but depending on your set-up, ShedLock DB operations may
end-up being part of the enclosing transaction.

If you need to configure the table name, schema or column names, you can use jOOQ render mapping as
described [here](https://github.com/lukas-krecan/ShedLock/issues/1830#issuecomment-2015820509).

#### Micronaut Data Jdbc
If you are using Micronaut data, and you do not want to add dependency on Spring JDBC, you can use
Micronaut JDBC support. Just be aware that it has just a basic functionality when compared to
the JdbcTemplate provider.

First, create lock table as described in the [JdbcTemplate](#jdbctemplate) section above.

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-micronaut</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.jdbc.micronaut.MicronautJdbcLockProvider;

...
@Singleton
public LockProvider lockProvider(TransactionOperations<Connection> transactionManager) {
    return new MicronautJdbcLockProvider(transactionManager);
}
```

#### Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;

...

@Bean
public LockProvider lockProvider(MongoClient mongo) {
    return new MongoLockProvider(mongo.getDatabase(databaseName))
}
```

Please note that MongoDB integration requires Mongo >= 2.4 and mongo-java-driver >= 3.7.0


#### Reactive Mongo
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-mongo-reactivestreams</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider;

...

@Bean
public LockProvider lockProvider(MongoClient mongo) {
    return new ReactiveStreamsMongoLockProvider(mongo.getDatabase(databaseName))
}
```

Please note that MongoDB integration requires Mongo >= 4.x and mongodb-driver-reactivestreams 1.x


#### DynamoDB 2
Depends on AWS SDK v2.

Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-dynamodb2</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.dynamodb2.DynamoDBLockProvider;

...

@Bean
public LockProvider lockProvider(software.amazon.awssdk.services.dynamodb.DynamoDbClient dynamoDB) {
    return new DynamoDBLockProvider(dynamoDB, "Shedlock");
}
```

> Please note that the lock table must be created externally with `_id` as a partition key.
> `DynamoDBUtils#createLockTable` may be used for creating it programmatically.
> A table definition is available from `DynamoDBLockProvider`'s Javadoc.

#### ZooKeeper (using Curator)
Import
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-zookeeper-curator</artifactId>
    <version>6.0.1</version>
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
    <version>6.0.1</version>
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

#### Redis (using Spring ReactiveRedisConnectionFactory)
Import
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-spring</artifactId>
    <version>6.0.1</version>
</dependency>
```

and configure

```java
import net.javacrumbs.shedlock.provider.redis.spring.ReactiveRedisLockProvider;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

...

@Bean
public LockProvider lockProvider(ReactiveRedisConnectionFactory connectionFactory) {
    return new ReactiveRedisLockProvider.Builder(connectionFactory)
        .environment(ENV)
        .build();
}
```

Redis lock provider uses classical lock mechanism as described [here](https://redis.io/commands/setnx#design-pattern-locking-with-codesetnxcode)
which may not be reliable in case of Redis master failure.

#### Redis (using Jedis)
Import
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-redis-jedis4</artifactId>
    <version>6.0.1</version>
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
    <artifactId>shedlock-provider-hazelcast4</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.hazelcast4.HazelcastLockProvider;

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
    <artifactId>shedlock-provider-couchbase-javaclient3</artifactId>
    <version>6.0.1</version>
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

For Couchbase 3 use `shedlock-provider-couchbase-javaclient3` module and `net.javacrumbs.shedlock.provider.couchbase3` package.

#### Elasticsearch
I am really not sure if it's a good idea to use Elasticsearch as a lock provider. But if you have no other choice, you can. Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-elasticsearch8</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import static net.javacrumbs.shedlock.provider.elasticsearch8.ElasticsearchLockProvider;

...

@Bean
public ElasticsearchLockProvider lockProvider(ElasticsearchClient client) {
    return new ElasticsearchLockProvider(client);
}
```

#### OpenSearch
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-opensearch</artifactId>
    <version>4.36.1</version>
</dependency>
```

Configure:

```java
import static net.javacrumbs.shedlock.provider.opensearch.OpenSearchLockProvider;

...

@Bean
public OpenSearchLockProvider lockProvider(RestHighLevelClient highLevelClient) {
    return new OpenSearchLockProvider(highLevelClient);
}
```

#### CosmosDB
CosmosDB support is provided by a third-party module available [here](https://github.com/jesty/shedlock-provider-cosmosdb)


#### Cassandra
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-cassandra</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider;
import net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider.Configuration;

...

@Bean
public CassandraLockProvider lockProvider(CqlSession cqlSession) {
    return new CassandraLockProvider(Configuration.builder().withCqlSession(cqlSession).withTableName("lock").build());
}
```

Example for creating default keyspace and table in local Cassandra instance:
```sql
CREATE KEYSPACE shedlock with replication={'class':'SimpleStrategy', 'replication_factor':1} and durable_writes=true;
CREATE TABLE shedlock.lock (name text PRIMARY KEY, lockUntil timestamp, lockedAt timestamp, lockedBy text);
```

Please, note that CassandraLockProvider uses Cassandra driver v4, which is part of Spring Boot since 2.3.

#### Consul
ConsulLockProvider has one limitation: lockAtMostFor setting will have a minimum value of 10 seconds. It is dictated by consul's session limitations.

Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-consul</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.consul.ConsulLockProvider;

...

@Bean // for micronaut please define preDestroy property @Bean(preDestroy="close")
public ConsulLockProvider lockProvider(com.ecwid.consul.v1.ConsulClient consulClient) {
    return new ConsulLockProvider(consulClient);
}
```

Please, note that Consul lock provider uses [ecwid consul-api client](https://github.com/Ecwid/consul-api), which is part of spring cloud consul integration (the `spring-cloud-starter-consul-discovery` package).

#### ArangoDB
Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-arangodb</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.arangodb.ArangoLockProvider;

...

@Bean
public ArangoLockProvider lockProvider(final ArangoOperations arangoTemplate) {
    return new ArangoLockProvider(arangoTemplate.driver().db(DB_NAME));
}
```

Please, note that ArangoDB lock provider uses ArangoDB driver v6.7, which is part of [arango-spring-data](https://github.com/arangodb/spring-data) in version 3.3.0.

#### Neo4j
Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-neo4j</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:
```java
import net.javacrumbs.shedlock.core.LockConfiguration;

...

@Bean
Neo4jLockProvider lockProvider(org.neo4j.driver.Driver driver) {
    return new Neo4jLockProvider(driver);
}
```

Please make sure that ```neo4j-java-driver``` version used by ```shedlock-provider-neo4j``` matches the driver version used in your
project (if you use `spring-boot-starter-data-neo4j`, it is probably provided transitively).

#### Etcd
Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-etcd-jetcd</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.etcd.jetcd.EtcdLockProvider;

...

@Bean
public LockProvider lockProvider(Client client) {
    return new EtcdLockProvider(client);
}
```


#### Apache Ignite
Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-ignite</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.ignite.IgniteLockProvider;

...

@Bean
public LockProvider lockProvider(Ignite ignite) {
    return new IgniteLockProvider(ignite);
}
```

#### In-Memory
If you want to use a lock provider in tests there is an in-Memory implementation.

Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-inmemory</artifactId>
    <version>6.0.1</version>
    <scope>test</scope>
</dependency>
```

```java
import net.javacrumbs.shedlock.provider.inmemory.InMemoryLockProvider;

...

@Bean
public LockProvider lockProvider() {
    return new InMemoryLockProvider();
}
```

#### Memcached (using spymemcached)
Please, be aware that memcached is not a database but a cache. It means that if the cache is full,
[the lock may be released prematurely](https://stackoverflow.com/questions/6868256/memcached-eviction-prior-to-key-expiry/10456364#10456364)
**Use only if you know what you are doing.**

Import
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-memcached-spy</artifactId>
    <version>6.0.1</version>
</dependency>
```

and configure

```java
import net.javacrumbs.shedlock.provider.memcached.spy.MemcachedLockProvider;

...

@Bean
public LockProvider lockProvider(net.spy.memcached.MemcachedClient client) {
    return new MemcachedLockProvider(client, ENV);
}
```

P.S.:

Memcached Standard Protocol:
- A key (arbitrary string up to 250 bytes in length. No space or newlines for ASCII mode)
- An expiration time, in `seconds`. '0' means never expire. Can be up to 30 days. After 30 days, is treated as a unix timestamp of an exact date. (support `seconds`、`minutes`、`days`, and less than `30` days)


#### Datastore

Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-datastore</artifactId>
    <version>6.0.1</version>
</dependency>
```

and configure
```java
import net.javacrumbs.shedlock.provider.datastore.DatastoreLockProvider;

...

@Bean
public LockProvider lockProvider(com.google.cloud.datastore.Datastore datastore) {
    return new DatastoreLockProvider(datastore);
}

```
#### Spanner
Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-spanner</artifactId>
    <version>6.0.1</version>
</dependency>
```
Configure
```java
import net.javacrumbs.shedlock.provider.spanner.SpannerLockProvider;

...

// Basic
@Bean
public LockProvider lockProvider(DatabaseClient databaseClient) {
    return new SpannerLockProvider(databaseClientSupplier);
}

// Custom host, table and column names
@Bean
public LockProvider lockProvider(DatabaseClient databaseClient) {
    var config = SpannerLockProvider.Configuration.builder()
        .withDatabaseClient(databaseClientSupplier)
        .withTableConfiguration(SpannerLockProvider.TableConfiguration.builder()
            ...
            // Custom table and column names
            .build())
        .withHostName("customHostName")
        .build();

    return new SpannerLockProvider(config);
}
```


## Multi-tenancy
If you have multi-tenancy use-case you can use a lock provider similar to this one
(see the full [example](https://github.com/lukas-krecan/ShedLock/blob/master/providers/jdbc/shedlock-provider-jdbc-template/src/test/java/net/javacrumbs/shedlock/provider/jdbctemplate/MultiTenancyLockProviderIntegrationTest.java#L87))
```java
private static abstract class MultiTenancyLockProvider implements LockProvider {
    private final ConcurrentHashMap<String, LockProvider> providers = new ConcurrentHashMap<>();

    @Override
    public @NonNull Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String tenantName = getTenantName(lockConfiguration);
        return providers.computeIfAbsent(tenantName, this::createLockProvider).lock(lockConfiguration);
    }

    protected abstract LockProvider createLockProvider(String tenantName) ;

    protected abstract String getTenantName(LockConfiguration lockConfiguration);
}
```

## Customization
You can customize the behavior of the library by implementing `LockProvider` interface. Let's say you want to implement
a special behavior after a lock is obtained. You can do it like this:

```java
public class MyLockProvider implements LockProvider {
    private final LockProvider delegate;

    public MyLockProvider(LockProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Optional<SimpleLock> lock = delegate.lock(lockConfiguration);
        if (lock.isPresent()) {
            // do something
        }
        return lock;
    }
}
```

You can see a full example in [TrackingLockProviderWrapper](https://github.com/lukas-krecan/ShedLock/blob/master/shedlock-core/src/main/java/net/javacrumbs/shedlock/util/TrackingLockProviderWrapper.java)

## Duration specification
All the annotations where you need to specify a duration support the following formats

* duration+unit - `1s`, `5ms`, `5m`, `1d` (Since 4.0.0)
* duration in ms - `100` (only Spring integration)
* ISO-8601 - `PT15M` (see [Duration.parse()](https://docs.oracle.com/javase/8/docs/api/java/time/Duration.html#parse-java.lang.CharSequence-) documentation)

## Extending the lock
There are some use-cases which require to extend currently held lock. You can use LockExtender in the
following way:

```java
LockExtender.extendActiveLock(Duration.ofMinutes(5), ZERO);
```

Please note that not all lock provider implementations support lock extension.

## KeepAliveLockProvider
There is also KeepAliveLockProvider that is able to keep the lock alive by periodically extending it. It can be
used by wrapping the original lock provider. My personal opinion is that it should be used only in special cases,
it adds more complexity to the library and the flow is harder to reason about so please use moderately.

```java
@Bean
public LockProvider lockProvider(...) {
    return new  KeepAliveLockProvider(new XyzProvider(...), scheduler);
}
```
KeepAliveLockProvider extends the lock in the middle of the lockAtMostFor interval. For example, if the lockAtMostFor
is 10 minutes the lock is extended every 5 minutes for 10 minutes until the lock is released. Please note that the minimal
lockAtMostFor time supported by this provider is 30s. The scheduler is used only for the lock extension, single thread
should be enough.

## Micronaut integration
Since version 4.0.0, it's possible to use Micronaut framework for integration

Import the project:
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <!-- Micronaut 3 -->
    <artifactId>shedlock-micronaut</artifactId>
    <!-- For Micronaut 4 use -->
    <!-- <artifactId>shedlock-micronaut4</artifactId> -->
    <version>6.0.1</version>
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

## CDI integration
Since version 5.0.0, it's possible to use CDI for integration (tested only with Quarkus)

Import the project:
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-cdi</artifactId>
    <version>6.0.1</version>
</dependency>
```

Configure default lockAtMostFor value (application.properties):
```properties
shedlock.defaults.lock-at-most-for=PT30S
```

Configure lock provider:
```java
@Produces
@Singleton
public LockProvider lockProvider() {
        ...
}
```

Configure the scheduled task:
```java
@Scheduled(every = "1s")
@SchedulerLock(name = "myTask")
public void myTask() {
    assertLocked();
    ...
}
```

The implementation only depends on `jakarta.enterprise.cdi-api` and `microprofile-config-api` so it should be
usable in other CDI compatible frameworks, but it has not been tested with anything else than Quarkus. It's
built on top of javax annotation as Quarkus has not moved to Jakarta EE namespace yet.

The support is minimalistic, for example there is no support for expressions in the annotation parameters yet,
if you need it, feel free to send a PR.

## Locking without a framework
It is possible to use ShedLock without a framework

```java
LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

...
Instant createdAt = Instant.now();
Duration lockAtMostFor = Duration.ofSeconds(60);
Duration lockAtLeastFor = Duration.ZERO;
executor.executeWithLock(runnable, new LockConfiguration(createdAt, "lockName", lockAtMostFor, lockAtLeastFor));

```

## Extending the lock
Some lock providers support extension of the lock. For the time being, it requires manual lock manipulation,
directly using `LockProvider` and calling `extend` method on the `SimpleLock`.

## Multiple LockProvider support in Spring
Since version 6.0.0 you can use multiple lock provider implementations. Just define them in your application context
and disambiguate them using `@LockProviderToUse("lockProviderBeanName")` annotation on method, class or package.
If the annotation is not found, the execution fails in the runtime, not in startup-time. If you need more dynamic resolution
of LockProviders, use a LockProvider wrapper as described in [Multi-tenancy](#multi-tenancy).

## Modes of Spring integration
ShedLock supports two modes of Spring integration. One that uses an AOP proxy around scheduled method (PROXY_METHOD)
and one that proxies TaskScheduler (PROXY_SCHEDULER)

#### Scheduled Method proxy
Since version 4.0.0, the default mode of Spring integration is an AOP proxy around the annotated method.

The main advantage of this mode is that it plays well with other frameworks that want to somehow alter the default Spring scheduling mechanism.
The disadvantage is that the lock is applied even if you call the method directly. If the method returns a value and the lock is held
by another process, null or an empty Optional will be returned (primitive return types are not supported).

Final and non-public methods are not proxied so either you have to make your scheduled methods public and non-final or use TaskScheduler proxy.

![Method proxy sequenceDiagram](https://github.com/lukas-krecan/ShedLock/raw/master/documentation/method_proxy.png)

#### TaskScheduler proxy (deprecated)
This mode wraps Spring `TaskScheduler` in an AOP proxy. **This mode does not play well with instrumentation libraries**
like opentelementry that also wrap TaskScheduler. Please only use it if you know what you are doing.
It can be switched-on like this (PROXY_SCHEDULER was the default method before 4.0.0):

```java
@EnableSchedulerLock(interceptMode = PROXY_SCHEDULER)
```

If you do not specify your task scheduler, a default one is created for you. If you have special needs, just create a bean implementing `TaskScheduler`
interface, and it will get wrapped into the AOP proxy automatically.

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
To prevent misconfiguration errors, like AOP misconfiguration, missing annotation etc., you can assert that the lock
works by using LockAssert:

```java
@Scheduled(...)
@SchedulerLock(..)
public void scheduledTask() {
    // To assert that the lock is held (prevents misconfiguration errors)
    LockAssert.assertLocked();
    // do something
}
```

In unit tests you can switch-off the assertion by calling `LockAssert.TestHelper.makeAllAssertsPass(true)` on given thread (as in this [example](https://github.com/lukas-krecan/ShedLock/commit/e8d63b7c56644c4189e0a8b420d8581d6eae1443)).

## Kotlin gotchas
The library is tested with Kotlin and works fine. The only issue is Spring AOP which does not work on final method. If you use `@SchedulerLock` with `@Component`
annotation, everything should work since Kotlin Spring compiler plugin will automatically 'open' the method for you. If `@Component` annotation is not present, you
have to open the method by yourself. (see [this issue](https://github.com/lukas-krecan/ShedLock/issues/1268) for more details)

## Caveats
Locks in ShedLock have an expiration time which leads to the following possible issues.
1. If the task runs longer than `lockAtMostFor`, the task can be executed more than once
2. If the clock difference between two nodes is more than `lockAtLeastFor` or minimal execution time the task can be
executed more than once.

## Troubleshooting
Help, ShedLock does not do what it's supposed to do!

1. Upgrade to the newest version
2. Use [LockAssert](https://github.com/lukas-krecan/ShedLock#lock-assert) to ensure that AOP is correctly configured.
   - If it does not work, please read about Spring AOP internals (for example [here](https://docs.spring.io/spring-framework/docs/current/reference/html/core.html#aop-proxying))
3. Check the storage. If you are using JDBC, check the ShedLock table. If it's empty, ShedLock is not properly configured.
If there is more than one record with the same name, you are missing a primary key.
4. Use ShedLock debug log. ShedLock logs interesting information on DEBUG level with logger name `net.javacrumbs.shedlock`.
It should help you to see what's going on.
5. For short-running tasks consider using `lockAtLeastFor`. If the tasks are short-running, they could be executed one
after another, `lockAtLeastFor` can prevent it.


# Release notes
See [here](RELEASES.md)
