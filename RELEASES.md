# Release notes

## 6.0.0
* Support for multiple LockProviders
* cdi-vintage module removed
* Micronaut 3 support removed
* micronaut-jdbc built on top of Micronaut 4
* PROXY_SCHEDULER mode deprecated
* Dependency updates

## 5.16.0 (2024-09-06)
* Support for custome partition key for Dynamo #2128 (thanks @kumar-himans)
* Upgrade OpenSearch rest-high-level-client #2115 - Breaking change due to rest-high-level-client backward incompatibility
* Dependency updates

## 5.15.1 (2024-08-27)
* Fix for Neo4j Enterprise #2099 (thanks @tle130475c)
* Dependency updates

## 5.15.0 (2024-08-15)
* Dependency updates
* ElasticSearch updated to 8.15.0 containing backward incompatible change (thanks @mputz86)

## 5.14.0 + 4.48.0 (2024-07-24)
* RedisLockProvider made extensible (thanks @shubhajyoti-bagchi-groww)
* Dependency updates

## 5.13.0 (2024-04-05)
* #1779 Ability to rethrow unexpected exception in JdbcTemplateStorageAccessor
* Dependency updates

## 5.12.0 (2024-02-29)
* #1800 Enable lower case for database type when using usingDbTime()
* #1804 Startup error with Neo4j 5.17.0
* Dependency updates

## 4.47.0 (2024-03-01)
* #1800 Enable lower case for database type when using usingDbTime() (thanks @yuagu1)

## 5.11.0 (2024-02-13)
* #1753 Fix SpEL for methods with parameters
* Dependency updates

## 5.10.2 (2023-12-07)
* #1635 fix makeAllAssertsPass locks only once
* Dependency updates

## 5.10.1 (2023-12-06)
* #1635 fix makeAllAssertsPass(false) throws NoSuchElementException
* Dependency updates

## 5.10.0 (2023-11-07)
* SpannerLockProvider added (thanks @pXius)
* Dependency updates

## 5.9.1 (2023-10-19)
* QuarkusRedisLockProvider supports Redis 6.2 (thanks  @ricardojlrufino)

## 5.9.0 (2023-10-15)
* Support Quarkus 2 Redis client (thanks  @ricardojlrufino)
* Better handling of timeouts in ReactiveStreamsMongoLockProvider
* Dependency updates

## 5.8.0 (2023-09-15)
* Support for Micronaut 4
* Use Merge instead of Insert for Oracle #1528 (thanks @xmojsic)
* Dependency updates

## 5.7.0 (2023-08-25)
* JedisLockProvider supports extending (thanks @shotmk)
* Better behavior when locks are nested #1493

## 4.46.0 (2023-09-05)
* JedisLockProvider (version 3) supports extending (thanks @shotmk)

## 4.45.0 (2023-09-04)
* JedisLockProvider supports extending (thanks @shotmk)

## 5.6.0
* Ability to explicitly set database product in JdbTemplateLockProvider (thanks @metron2)
* Removed forgotten versions from BOM
* Dependency updates

## 5.5.0 (2023-06-19)
* Datastore support (thanks @mmastika)
* Dependency updates

## 5.4.0 (2023-06-06)
* Handle [uncategorized SQL exceptions](https://github.com/lukas-krecan/ShedLock/pull/1442) (thanks @jaam)
* Dependency updates

## 5.3.0 (2023-05-13)
* Added shedlock-cdi module (supports newest CDI version)
* Dependency updates

## 5.2.0 (2023-03-06)
* Uppercase in JdbcTemplateProvider (thanks @Ragin-LundF)
* Dependency updates

## 5.1.0 (2023-01-07)
* Added SpEL support to @SchedulerLock name attribute (thanks @ipalbeniz)
* Dependency updates

## 5.0.1 (2022-12-10)
* Work around broken Spring 6 exception translation https://github.com/lukas-krecan/ShedLock/issues/1272

## 4.44.0 (2022-12-29)
* Insert ignore for MySQL https://github.com/lukas-krecan/ShedLock/commit/8a4ae7ad8103bb47f55d43bccf043ca261c24d7a

## 5.0.0 (2022-12-10)
* Requires JDK 17
* Tested with Spring 6 (Spring Boot 3)
* Micronaut updated to 3.x.x
* R2DBC 1.x.x (still sucks)
* Spring Data 3.x.x
* Rudimentary support for CDI (tested with quarkus)
* New jOOQ lock provider
* SLF4j 2
* Deleted all deprecated code and support for old versions of libraries

## 4.43.0 (2022-12-04)
* Better logging in JdbcTemplateProvider
* Dependency updates

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
