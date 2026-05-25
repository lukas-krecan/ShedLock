# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Test Commands

```bash
# Build all modules (skipping tests)
./mvnw install -DskipTests

# Build and run all tests (most tests require Docker for Testcontainers)
./mvnw verify

# Build/test a specific module
./mvnw verify -pl providers/jdbc/shedlock-provider-jdbc-template

# Run a single test class
./mvnw test -pl providers/jdbc/shedlock-provider-jdbc-template -Dtest=H2JdbcTemplateLockProviderIntegrationTest

# Run spotless formatter (auto-fix)
./mvnw spotless:apply

# Check formatting without fixing
./mvnw spotless:check
```

## Code Formatting

Code formatting is enforced via the Spotless Maven plugin:
- Java: **Palantir Java Format** style
- Kotlin: **ktfmt** with `KOTLINLANG` style, max width 120

Run `./mvnw spotless:apply` before committing. The `validate` phase runs the check automatically.

## Architecture Overview

ShedLock is a distributed lock library for scheduled tasks. It ensures a task runs on at most one node at a time using an external store for coordination.

### Core Layer (`shedlock-core`)

The central abstractions live in `net.javacrumbs.shedlock.core`:
- **`LockProvider`** — the primary interface: `Optional<SimpleLock> lock(LockConfiguration)`
- **`SimpleLock`** — returned on success; callers must call `unlock()` after task completion
- **`LockConfiguration`** — holds lock name, `lockAtMostFor`, and `lockAtLeastFor` durations
- **`LockingTaskExecutor`** / **`DefaultLockingTaskExecutor`** — wraps a task with lock acquisition
- **`LockManager`** / **`DefaultLockManager`** — integrates with framework schedulers
- **`LockAssert`** — allows task code to assert it holds a lock

Support infrastructure in `net.javacrumbs.shedlock.support`:
- **`StorageBasedLockProvider`** — base class for all storage-backed providers; implements insert-then-update locking logic with an in-memory `LockRecordRegistry` cache
- **`StorageAccessor`** — interface implemented by each backend: `insertRecord`, `updateRecord`, `unlock`, `extend`
- **`AbstractStorageAccessor`** — abstract base for `StorageAccessor` implementations

### Framework Integration

- **`spring/shedlock-spring`** — Spring `@SchedulerLock` annotation support
- **`cdi/shedlock-cdi`** — CDI/Quarkus integration
- **`micronaut/shedlock-micronaut4`** — Micronaut integration

### Lock Providers (`providers/`)

Each provider is an independent Maven module. The two-tier pattern for SQL providers:

1. **`providers/sql/shedlock-sql-support`** — database-agnostic SQL generation
   - `SqlStatementsSource` — generates INSERT/UPDATE/UNLOCK SQL; factory picks the right subclass
   - `DatabaseProduct` enum — matches JDBC product names; subclasses override SQL for each DB
   - Server-time variants (`*ServerTimeStatementsSource`) use DB-side timestamps instead of client clock
   - Supported with server time: Postgres, CockroachDB, SQL Server, Oracle, MySQL/MariaDB, HSQL, H2, DB2, **Snowflake**

2. **`providers/jdbc/shedlock-provider-jdbc-internal`** — shared JDBC statement execution (`AbstractJdbcStorageAccessor`)

3. Concrete provider modules (each depends on the above):
   - `shedlock-provider-jdbc-template` — Spring `JdbcTemplate`
   - `shedlock-provider-jdbc` — plain JDBC `DataSource`
   - `shedlock-provider-jooq` — jOOQ
   - `shedlock-provider-r2dbc` — R2DBC (reactive)
   - `shedlock-provider-exposed` — Kotlin Exposed

Non-SQL providers follow the same `StorageAccessor` pattern but with their own storage-specific logic (Mongo, Redis, DynamoDB, ZooKeeper, Cassandra, etc.).

### Test Support

- **`shedlock-test-support`** — base integration test classes: `AbstractLockProviderIntegrationTest`, `AbstractStorageBasedLockProviderIntegrationTest`
- **`providers/jdbc/shedlock-test-support-jdbc`** — JDBC-specific test infrastructure with per-DB `DbConfig` classes (H2, Postgres, MySQL, etc.) using Testcontainers
- Test pattern: a concrete test class extends `Abstract*IntegrationTest` and supplies a `DbConfig`

### Adding a New SQL Database

1. Add the `DatabaseProduct` enum value in `shedlock-sql-support`
2. Create a `*ServerTimeStatementsSource` subclass with DB-specific SQL (if server-time is needed)
3. Register in `SqlStatementsSource.createDbTimeStatementSource()` switch
4. Add a `DbConfig` in `shedlock-test-support-jdbc`
5. Add a test class in the provider module(s) that extends the abstract integration test

### Lock Table Schema

```sql
CREATE TABLE shedlock (
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

Column names are configurable via `SqlConfiguration.ColumnNames`. Table name defaults to `shedlock`.
