# ShedLock Code Review

This file contains a code review of the ShedLock codebase, identifying potential bugs, API design issues, and code quality improvements. Issues are ordered roughly by severity.

---

## 1. `AbstractSimpleLock.extend()` invalidates the lock even on a failed extension

**File:** `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/AbstractSimpleLock.java:37–43`

```java
public Optional<SimpleLock> extend(Duration lockAtMostFor, Duration lockAtLeastFor) {
    checkValidity();
    Optional<SimpleLock> result = doExtend(...);
    valid = false;          // runs unconditionally, even if result is empty
    return result;
}
```

When `doExtend()` returns `Optional.empty()` (extension failed because the lock already
expired), `valid` is still set to `false`. This means the caller can no longer call
`unlock()` on the old lock. The lock will remain held in the database until `lockAtMostFor`
expires with no way to release it early.

**Proposed fix:** Only invalidate if extension succeeded:

```java
Optional<SimpleLock> result = doExtend(...);
if (result.isPresent()) {
    valid = false;
}
return result;
```

The `SimpleLock` Javadoc should then be updated: a failed `extend()` (empty Optional)
leaves the lock usable for `unlock()`, while a successful extension invalidates it as before.

---

## 2. Inconsistency: throwing vs. returning empty from `doExtend()` leaves `valid` in different states

**File:** `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/AbstractSimpleLock.java:37–43`

This is a follow-on from issue 1. If `doExtend()` *throws* (e.g., `KeepAliveLock` throws
`UnsupportedOperationException`), the `valid = false` assignment on line 41 is never reached
— the lock stays valid. If `doExtend()` returns `Optional.empty()`, `valid` is set to false.

The lock's validity after a failed extension therefore depends on *how* the failure is
reported, which is invisible to the caller. One consistent rule should be picked and
documented. The fix from issue 1 (only invalidate on success) also resolves this inconsistency.

---

## 3. `ClockProvider.clock` is not `volatile`

**File:** `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/ClockProvider.java:22–29`

```java
private static Clock clock = Clock.systemUTC();   // plain static field

public static void setClock(Clock clock) {
    ClockProvider.clock = clock;                   // unguarded write
}

public static Instant now() {
    return clock.instant().truncatedTo(...);        // unguarded read
}
```

Without `volatile`, the Java Memory Model gives no visibility guarantee between threads.
A write in one thread (e.g., test setup calling `setClock()`) may not be visible to another
thread reading `clock` in `now()`. In practice this affects tests more than production, but
Spring's parallel test execution can expose it as a flaky failure.

**Fix:**

```java
private static volatile Clock clock = Clock.systemUTC();
```

---

## 4. `unlock()` silently swallows the failure after 10 retries

**File:** `providers/jdbc/shedlock-provider-jdbc-template/src/main/java/net/javacrumbs/shedlock/provider/jdbctemplate/JdbcTemplateStorageAccessor.java:100–110`

```java
public void unlock(LockConfiguration lockConfiguration) {
    for (int i = 0; i < 10; i++) {
        try {
            doUnlock(lockConfiguration);
            return;
        } catch (ConcurrencyFailureException | TransactionSystemException e) {
            logger.info("Unlock failed … retrying attempt {}", i + 1);
        }
    }
    logger.error("Unlock failed after 10 attempts");   // returns normally
}
```

After 10 failures the method returns without throwing. `AbstractSimpleLock.unlock()` then
sets `valid = false`, so the application believes the lock was released. The database record
still has `lock_until` set to the full `lockAtMostFor` expiry — which could be minutes or
hours — blocking other nodes for the entire duration with no programmatic indication of the
failure (only a log line).

**Proposed fix:** Throw after exhausting retries:

```java
throw new LockException("Unlock failed after 10 attempts for lock: " + lockConfiguration.getName());
```

This ensures the failure propagates to the task executor and is not silently lost.

---

## 5. `extend()` in `JdbcTemplateStorageAccessor` has no exception handling

**File:** `providers/jdbc/shedlock-provider-jdbc-template/src/main/java/net/javacrumbs/shedlock/provider/jdbctemplate/JdbcTemplateStorageAccessor.java:91–97`

```java
@Override
public boolean extend(LockConfiguration lockConfiguration) {
    String sql = sqlStatementsSource().getExtendStatement();
    logger.debug("Extending lock={}…", …);
    return execute(sql, lockConfiguration);   // no catch
}
```

`insertRecord()` and `updateRecord()` both catch `DuplicateKeyException |
ConcurrencyFailureException | TransactionSystemException` and return `false`. `extend()`
does not — a serialization conflict will propagate as an unchecked exception instead of
returning `false` (which `StorageBasedLockProvider` would translate to `Optional.empty()`).
This breaks the contract callers rely on and is inconsistent with the sibling methods.

**Proposed fix:** Apply the same exception handling as `updateRecord()`:

```java
@Override
public boolean extend(LockConfiguration lockConfiguration) {
    String sql = sqlStatementsSource().getExtendStatement();
    logger.debug("Extending lock={} until={}", lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    try {
        return execute(sql, lockConfiguration);
    } catch (DuplicateKeyException | ConcurrencyFailureException | TransactionSystemException e) {
        logger.debug("Serialization exception when extending lock", e);
        return false;
    } catch (DataAccessException e) {
        logger.error("Unexpected exception when extending lock", e);
        throw new LockException(e);
    }
}
```

---

## 6. `sqlStatementsSource()` synchronizes on an external object

**File:** `providers/jdbc/shedlock-provider-jdbc-template/src/main/java/net/javacrumbs/shedlock/provider/jdbctemplate/JdbcTemplateStorageAccessor.java:135–142`

```java
private SqlStatementsSource sqlStatementsSource() {
    synchronized (configuration) {           // synchronizing on a caller-visible object
        if (sqlStatementsSource == null) {
            sqlStatementsSource = SqlStatementsSource.create(configuration);
        }
        return sqlStatementsSource;
    }
}
```

Synchronizing on `configuration` (an object passed in by the caller) is a well-known
anti-pattern. External code that also synchronizes on the same `Configuration` instance
could create an unintended deadlock. The pattern also makes lock ordering impossible to
reason about from the outside.

**Proposed fix:** Use a dedicated private lock object:

```java
private final Object sqlStatementsSourceLock = new Object();

private SqlStatementsSource sqlStatementsSource() {
    synchronized (sqlStatementsSourceLock) {
        if (sqlStatementsSource == null) {
            sqlStatementsSource = SqlStatementsSource.create(configuration);
        }
        return sqlStatementsSource;
    }
}
```

---

## 7. `LockRecordRegistry`'s `WeakHashMap` provides no benefit for the common case

**File:** `shedlock-core/src/main/java/net/javacrumbs/shedlock/support/LockRecordRegistry.java:26`

```java
private final Set<String> lockRecords =
    Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
```

Lock names from `@SchedulerLock(name = "myTask")` are compile-time string constants that
the JVM interns. Interned strings are held by the JVM's string pool and are never
garbage-collected. The `WeakHashMap` therefore never evicts them for the common case, and
the set grows monotonically with the number of distinct lock names — the same behaviour as
a plain `HashSet`.

The `WeakHashMap` is also slightly more expensive than `HashSet` and adds cognitive overhead.
Additionally, wrapping it with `Collections.synchronizedSet()` is itself a smell since
compound check-then-act operations (`contains` + `add`) are not atomic under the wrapper.
The current callers in `StorageBasedLockProvider` are safe because they are not concurrent,
but the data structure implies a thread-safety guarantee that it does not fully deliver.

**Proposed fix:** Use a regular `HashSet` (or `ConcurrentHashMap`-backed set for explicit
thread safety) and add a comment explaining that the cache is bounded by the number of
unique lock names, which is small and finite in typical applications:

```java
// Bounded by the number of distinct lock names configured in the application.
private final Set<String> lockRecords = Collections.synchronizedSet(new HashSet<>());
```

---

## 8. `AbstractSimpleLock.valid` is not thread-safe

**File:** `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/AbstractSimpleLock.java:20,28–31`

```java
private boolean valid = true;   // plain field

public final void unlock() {
    checkValidity();            // read: if (!valid) throw
    doUnlock();
    valid = false;              // write
}
```

`checkValidity()` and the subsequent `valid = false` write are not in a synchronized block.
If two threads simultaneously call `unlock()` on the same instance, both can pass
`checkValidity()` before either sets `valid = false`, causing `doUnlock()` to be called
twice.

While ShedLock's primary design assumes locks are used from a single thread,
`KeepAliveLockProvider` introduces a background `ScheduledExecutorService` thread that
accesses the inner lock concurrently with the task thread. `KeepAliveLock` adds its own
`synchronized(this)` blocks to protect `doUnlock()` and `extendForNextPeriod()`, but the
`valid` field check in `AbstractSimpleLock` sits outside those blocks.

**Proposed fix:** Replace `boolean valid` with `AtomicBoolean` and use compare-and-set:

```java
private final AtomicBoolean valid = new AtomicBoolean(true);

private void checkValidity() {
    if (!valid.get()) {
        throw new IllegalStateException("Lock " + lockConfiguration.getName()
                + " is not valid, it has already been unlocked or extended");
    }
}

// In unlock():
public final void unlock() {
    if (!valid.compareAndSet(true, false)) {
        throw new IllegalStateException(…);
    }
    doUnlock();
}
```

---

## 9. "This should never happen" branch should throw, not warn

**File:** `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/DefaultLockingTaskExecutor.java:73–81`

```java
finally {
    LockAssert.endLock();
    SimpleLock activeLock = LockExtender.endLock();
    if (activeLock != null) {
        activeLock.unlock();
    } else {
        // This should never happen, but I do not know any better way to handle the null case.
        logger.warn("No active lock, please report this as a bug.");
        lock.get().unlock();   // falls back to original lock reference
    }
}
```

The comment explicitly says this branch indicates a bug. Logging a warning and recovering
silently is the worst of both worlds: it hides the bug in production and makes it very
hard to reproduce. If this branch is truly unreachable, an `IllegalStateException` will
surface the bug immediately. The fallback `lock.get().unlock()` is also wrong in the
case where the task extended the lock during execution — it would unlock a lock that has
already been invalidated by the extension.

**Proposed fix:**

```java
} else {
    throw new IllegalStateException(
            "No active lock found in LockExtender for '" + lockName + "'. Please report this as a bug.");
}
```

---

## 10. `LockAssert.endLock()` and `LockExtender.endLock()` have asymmetric null-safety

**Files:**
- `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/LockAssert.java:44–50`
- `shedlock-core/src/main/java/net/javacrumbs/shedlock/core/LockExtender.java:52–58`

```java
// LockAssert
static void endLock() {
    Deque<String> activeLocks = activeLocks();
    activeLocks.removeLast();   // throws NoSuchElementException if deque is empty
    ...
}

// LockExtender
static SimpleLock endLock() {
    SimpleLock lock = locks().pollLast();   // returns null if deque is empty
    ...
}
```

Both methods are called sequentially in the `finally` block of `DefaultLockingTaskExecutor`:

```java
LockAssert.endLock();           // if this throws...
SimpleLock activeLock = LockExtender.endLock();  // ...this never runs
```

If `LockAssert.endLock()` throws `NoSuchElementException` (due to a framework interception
or misuse that calls `startLock` but not `endLock`), `LockExtender.endLock()` is skipped.
The `SimpleLock` leaks into the thread-local of a pooled thread, causing the next scheduled
task that runs on that thread to find a pre-existing lock and skip execution.

**Proposed fix:** Wrap each call in its own `try-finally`, or switch `LockAssert.endLock()`
to use `pollLast()` defensively:

```java
// In DefaultLockingTaskExecutor.executeWithLock finally block:
try {
    LockAssert.endLock();
} finally {
    SimpleLock activeLock = LockExtender.endLock();
    // ... unlock activeLock
}
```

---

## 11. `JdbcTemplateLockProvider.ColumnNames` is an empty subclass

**File:** `providers/jdbc/shedlock-provider-jdbc-template/src/main/java/net/javacrumbs/shedlock/provider/jdbctemplate/JdbcTemplateLockProvider.java:206–210`

```java
public static final class ColumnNames extends SqlConfiguration.ColumnNames {
    public ColumnNames(String name, String lockUntil, String lockedAt, String lockedBy) {
        super(name, lockUntil, lockedAt, lockedBy);
    }
}
```

This class adds no behaviour, no fields, and no documentation. It exists only as a
namespaced alias for `SqlConfiguration.ColumnNames`. Users navigating the API have to
discover the parent type anyway, the extra type adds an unnecessary import, and it creates
a migration burden if the hierarchy ever needs to change. It can be removed; call-sites
use `SqlConfiguration.ColumnNames` directly.

---

## Summary

| # | File | Issue | Severity |
|---|------|-------|----------|
| 1 | `AbstractSimpleLock.java:41` | `valid=false` on failed `extend()` makes lock unreleasable | **High** |
| 2 | `AbstractSimpleLock.java:37–43` | Throw vs. empty Optional from `doExtend()` leave `valid` in inconsistent states | **Medium** |
| 3 | `ClockProvider.java:22` | `clock` not `volatile` — visibility race in multi-threaded tests | Low |
| 4 | `JdbcTemplateStorageAccessor.java:108–110` | Silent swallow of `unlock()` failure after 10 retries | **High** |
| 5 | `JdbcTemplateStorageAccessor.java:91–97` | `extend()` missing exception handling, inconsistent with insert/update | **Medium** |
| 6 | `JdbcTemplateStorageAccessor.java:136` | Synchronizes on external `configuration` object | Low |
| 7 | `LockRecordRegistry.java:26` | `WeakHashMap` never evicts interned string constants | Low |
| 8 | `AbstractSimpleLock.java:20` | `valid` not thread-safe; `KeepAliveLockProvider` accesses lock from background thread | **Medium** |
| 9 | `DefaultLockingTaskExecutor.java:76–80` | Known-bug branch logs warning and recovers silently instead of throwing | Low |
| 10 | `LockAssert.java:46` / `LockExtender.java:53` | `removeLast()` vs. `pollLast()` asymmetry can leak `SimpleLock` into thread-local | **Medium** |
| 11 | `JdbcTemplateLockProvider.java:206` | `ColumnNames` subclass adds nothing | Trivial |
