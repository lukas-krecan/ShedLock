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

+ [Components](#components)
+ [Usage](#usage)
+ [Lock Providers](#configure-lockprovider)
  - [JdbcTemplate](#jdbctemplate)
  - [R2DBC](#r2dbc)
  - [Micronaut Data Jdbc](#micronaut-data-jdbc)
  - [Mongo](#mongo)
  - [DynamoDB](#dynamodb)
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
  - [Multi-tenancy](#Multi-tenancy)
  - [In-Memory](#In-Memory)
  - [Memcached](#Memcached)
+ [Duration specification](#duration-specification)
+ [Extending the lock](#extending-the-lock)
+ [Micronaut integration](#micronaut-integration)
+ [Locking without a framework](#locking-without-a-framework)
+ [Troubleshooting](#troubleshooting)
+ [Modes of Spring integration](#modes-of-spring-integration)
  - [Scheduled method proxy](#scheduled-method-proxy)
  - [TaskScheduler proxy](#taskscheduler-proxy)
+ [Release notes](#release-notes)

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
    <version>4.42.0</version>
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
executing node dies. This is just a fallback, under normal circumstances the lock is released as soon the tasks finishes.
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
By setting `lockAtMostFor` we make sure that the lock is released even if the node dies and by setting `lockAtLeastFor`
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

Or use [this](micronaut/test/micronaut-jdbc/src/main/resources/db/liquibase-changelog.xml) liquibase change-set.

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>4.42.0</version>
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

For more fine-grained configuration use other options of the `Configuration` object

```java
new JdbcTemplateLockProvider(builder()
    .withTableName("shdlck")
    .withColumnNames(new ColumnNames("n", "lck_untl", "lckd_at", "lckd_by"))
    .withJdbcTemplate(new JdbcTemplate(getDatasource()))
    .withLockedByValue("my-value")
    .build())
```

If you need to specify a schema, you can set it in the table name using the usual dot notation
`new JdbcTemplateLockProvider(datasource, "my_schema.shedlock")`

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
    <version>4.42.0</version>
</dependency>
```

and use it.

```java
@Override
protected LockProvider getLockProvider() {
    return new R2dbcLockProvider(connectionFactory);
}
```
I recommend using [R2DBC connection pool](https://github.com/r2dbc/r2dbc-pool), unless you are connecting to Oracle that does not work with the pool.


#### Micronaut Data Jdbc
If you are using Micronaut data and you do not want to add dependency on Spring JDBC, you can use
Micronaut JDBC support. Just be aware that it has just a basic functionality when compared to
the JdbcTemplate provider.

First, create lock table as described in the [JdbcTemplate](#jdbctemplate) section above.

Add dependency

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-micronaut</artifactId>
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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


#### DynamoDB
This depends on AWS SDK v1.

Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-dynamodb</artifactId>
    <version>4.42.0</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.dynamodb.DynamoDBLockProvider;

...

@Bean
public LockProvider lockProvider(com.amazonaws.services.dynamodbv2.document.DynamoDB dynamoDB) {
    return new DynamoDBLockProvider(dynamoDB.getTable("Shedlock"));
}
```

> Please note that the lock table must be created externally with `_id` as a partition key.
> `DynamoDBUtils#createLockTable` may be used for creating it programmatically.
> A table definition is available from `DynamoDBLockProvider`'s Javadoc.

#### DynamoDB 2
This depends on AWS SDK v2.

Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-dynamodb2</artifactId>
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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

If you are still using Spring Data Redis 1, import special lock provider `shedlock-provider-redis-spring-1` which works around
issue #105 or upgrade to Spring Data Redis 2 or higher.


#### Redis (using Jedis)
Import
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <!-- Jedis < 4 -->
    <!-- <artifactId>shedlock-provider-redis-jedis</artifactId> -->
    <!-- For Jedis >= 4 -->
    <artifactId>shedlock-provider-redis-jedis4</artifactId>
    <version>4.42.0</version>
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
    <!-- Hazelcast < 4 support is dropped -->
    <!-- Hazelcast >= 4 -->
    <artifactId>shedlock-provider-hazelcast4</artifactId>
    <version>4.42.0</version>
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
    <!-- Couchbase < 3 -->
    <artifactId>shedlock-provider-couchbase-javaclient</artifactId>
    <!-- Couchbase >= 3 -->
    <!-- <artifactId>shedlock-provider-couchbase-javaclient3</artifactId> -->
    <version>4.42.0</version>
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
I am really not sure it's a good idea to use Elasticsearch as a lock provider. But if you have no other choice, you can. Import the project

```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-elasticsearch8</artifactId>
    <version>4.42.0</version>
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
    <version>4.42.0</version>
</dependency>
```

Configure:

```java
import net.javacrumbs.shedlock.provider.cassandra.CassandraLockProvider;

...

@Bean
public CassandraLockProvider lockProvider(CqlSession cqlSession) {
    return new CassandraLockProvider(Configuration.builder().withCqlSession(session));
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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

#### Multi-tenancy
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

#### In-Memory
If you want to use a lock provider in tests there is an in-Memory implementation.

Import the project
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-inmemory</artifactId>
    <version>4.42.0</version>
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
    <version>4.42.0</version>
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
lockAtMostFor time supported by this provider is 30s.

## Micronaut integration
Since version 4.0.0, it's possible to use Micronaut framework for integration

Import the project:
```xml
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-micronaut</artifactId>
    <version>4.42.0</version>
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

## Extending the lock
Some lock providers support extension of the lock. For the time being, it requires manual lock manipulation,
directly using `LockProvider` and calling `extend` method on the `SimpleLock`.

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

#### TaskScheduler proxy
This mode wraps Spring `TaskScheduler` in an AOP proxy. **This mode does not play well with instrumentation libraries**
like opentelementry that also wrap TaskScheduler. Please only use it if you know what you are doing.
It can be switched-on like this (PROXY_SCHEDULER was the default method before 4.0.0):

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
The library is tested with Kotlin and works fine. The only issue is Spring AOP which does not work on final method. If you use `@SchedulerLock` with `@Scheduled`
annotation, everything should work since Kotlin Spring compiler plugin will automatically 'open' the method for you. If `@Scheduled` annotation is not present, you
have to open the method by yourself.

## Caveats
Locks in ShedLock have an expiration time which leads to the following possible issues.
1. If the task runs longer than `lockAtMostFor`, the task can be executed more than once
2. If the clock difference between two nodes is more than `lockAtLeastFor` or minimal execution time the task can be
executed more than once.

## Troubleshooting
Help, ShedLock does not do what it's supposed to do!

1. Upgrade to the newest version
2. Use [LockAssert](https://github.com/lukas-krecan/ShedLock#lock-assert) to ensure that AOP is correctly configured.
3. Check the storage. If you are using JDBC, check the ShedLock table. If it's empty, ShedLock is not properly configured.
If there is more than one record with the same name, you are missing a primary key.
4. Use ShedLock debug log. ShedLock logs interesting information on DEBUG level with logger name `net.javacrumbs.shedlock`.
It should help you to see what's going on.
5. For short-running tasks consider using `lockAtLeastFor`. If the tasks are short-running, they could be executed one
after another, `lockAtLeastFor` can prevent it.


## Requirements and dependencies
* Java 8
* slf4j-api

# Release notes
## 4.42.0 (2022-09-16)
* Deprecate old Couchbase lock provider
* Dependency updates

## 4.41.0 (2022-08-17)
* Couchbase collection support (thanks @mesuutt)
* Dependency updates

## 4.40.0 (2022-08-11)
* Fixed caching issues when the app is started by the DB does not exist yet (#1129)
* Dependency updates

## 4.39.0 (2022-07-26)
* Introduced elasticsearch8 LockProvider and deperecated the orignal one (thanks @MarAra)
* Dependency updates

## 4.38.0 (2022-07-02)
* ReactiveRedisLockProvider added (thanks @ericwcc)
* Dependency updates

## 4.37.0 (2022-06-14)
* OpenSearch provider (thanks @Pinny3)
* Fix wrong reference to reactive Mongo in BOM #1048
* Dependency updates

## 4.36.0 (2022-05-28)
* shedlock-bom module added
* Dependency updates

## 4.35.0 (2022-05-16)
* Neo4j allows to specify database thanks @SergeyPlatonov
* Dependency updates

## 4.34.0 (2022-04-09)
* Dropped support for Hazelcast <= 3 as it has unfixed vulnerability
* Dropped support for Spring Data Redis 1 as it is not supported
* Dependency updates

## 4.33.0
* memcached provider added (thanks @pinkhello)
* Dependency updates

## 4.32.0
* JDBC provider does not change autocommit attribute
* Dependency updates

## 4.31.0
* Jedis 4 lock provider
* Dependency updates

## 4.30.0
* In-memory lock provider added (thanks @kkocel)
* Dependency updates

## 4.29.0
* R2DBC support added (thanks @sokomishalov)
* Library upgrades

## 4.28.0
* Neo4j lock provider added (thanks @thimmwork)
* Library upgrades

## 4.27.0
* Ability to set transaction isolation in JdbcTemplateLockProvider
* Library upgrades

## 4.26.0
* KeepAliveLockProvider introduced
* Library upgrades

## 4.25.0
* LockExtender added

## 4.24.0
* Support for Apache Ignite (thanks @wirtsleg)
* Library upgrades

## 4.23.0
* Ability to set serialConsistencyLevel in Cassandra (thanks @DebajitKumarPhukan)
* Introduced shedlock-provider-jdbc-micronaut module (thanks @drmaas)

## 4.22.1
* Catching and logging Cassandra exception

## 4.22.0
* Support for custom keyspace in Cassandra provider

## 4.21.0
* Elastic unlock using IMMEDIATE refresh policy #422
* DB2 JDBC lock provider uses microseconds in DB time
* Various library upgrades

## 4.20.1
* Fixed DB JDBC server time #378

## 4.20.0
* Support for etcd (thanks grofoli)

## 4.19.1
* Fixed devtools compatibility #368

## 4.19.0
* Support for enhanced configuration in Cassandra provider (thanks DebajitKumarPhukan)
* LockConfigurationExtractor exposed as a Spring bean #359
* Handle CannotSerializeTransactionException #364

## 4.18.0
* Fixed Consul support for tokens and added enhanced Consul configuration (thanks DrWifey)

## 4.17.0
* Consul support for tokens

## 4.16.0
* Spring - EnableSchedulerLock.order param added to specify AOP proxy order
* JDBC - Log unexpected exceptions at ERROR level
* Hazelcast upgraded to 4.1

## 4.15.1
* Fix session leak in Consul provider #340 (thanks @haraldpusch)

## 4.15.0
* ArangoDB lock provider added (thanks @patrick-birkle)

## 4.14.0
* Support for Couchbase 3 driver (thanks @blitzenzzz)
* Removed forgotten configuration files form micronaut package (thanks @drmaas)
* Shutdown hook for Consul (thanks @kaliy)

## 4.13.0
* Support for Consul (thanks @kaliy)
* Various dependencies updated
* Deprecated default LockConfiguration constructor

## 4.12.0
* Lazy initialization of SqlStatementsSource #258

## 4.11.1
* MongoLockProvider uses mongodb-driver-sync
* Removed deprecated constructors from MongoLockProvider

## 4.10.1
* New Mongo reactive streams driver (thanks @codependent)

## 4.9.3
* Fixed JdbcTemplateLockProvider useDbTime() locking #244 thanks @gjorgievskivlatko

## 4.9.2
* Do not fail on DB type determining code if DB connection is not available

## 4.9.1
* Support for server time in DB2
* removed shedlock-provider-jdbc-internal module

## 4.9.0
* Support for server time in JdbcTemplateLockProvider
* Using custom non-null annotations
* Trimming time precision to milliseconds
* Micronaut upgraded to 1.3.4
* Add automatic DB tests for Oracle, MariaDB and MS SQL.

## 4.8.0
* DynamoDB 2 module introduced (thanks Mark Egan)
* JDBC template code refactored to not log error on failed insert in Postgres
    * INSERT .. ON CONFLICT UPDATE is used for Postgres

## 4.7.1
* Make LockAssert.TestHelper public

## 4.7.0
* New module for Hazelcasts 4
* Ability to switch-off LockAssert in unit tests

## 4.6.0
* Support for Meta annotations and annotation inheritance in Spring

## 4.5.2
* Made compatible with PostgreSQL JDBC Driver 42.2.11

## 4.5.1
* Inject redis template

## 4.5.0
* ClockProvider introduced
* MongoLockProvider(MongoDatabase) introduced

## 4.4.0
* Support for non-void returning methods when PROXY_METHOD interception is used

## 4.3.1
* Introduced shedlock-provider-redis-spring-1 to make it work around Spring Data Redis 1 issue #105 (thanks @rygh4775)

## 4.3.0
* Jedis dependency upgraded to 3.2.0
* Support for JedisCluster
* Tests upgraded to JUnit 5

## 4.2.0
* Cassandra provider (thanks @mitjag)

## 4.1.0
* More configuration option for JdbcTemplateProvider

## 4.0.4
* Allow configuration of key prefix in RedisLockProvider #181 (thanks @krm1312)

## 4.0.3
* Fixed junit dependency scope #179

## 4.0.2
* Fix NPE caused by Redisson #178
## 4.0.1
* DefaultLockingTaskExecutor made reentrant #175
## 4.0.0
Version 4.0.0 is a major release changing quite a lot of stuff
* `net.javacrumbs.shedlock.core.SchedulerLock` has been replaced by `net.javacrumbs.shedlock.spring.annotation.SchedulerLock`. The original annotation has been in wrong module and
was too complex. Please use the new annotation, the old one still works, but in few years it will be removed.
* Default intercept mode changed from `PROXY_SCHEDULER` to `PROXY_METHOD`. The reason is that there were a lot of issues with  `PROXY_SCHEDULER` (for example #168). You can still
use `PROXY_SCHEDULER` mode if you specify it manually.
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
