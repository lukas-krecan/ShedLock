package net.javacrumbs.shedlock.provider.mongo;

import com.github.fakemongo.Fongo;
import com.mongodb.MongoClient;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.bson.Document;
import org.junit.Test;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static com.mongodb.client.model.Filters.eq;
import static java.time.temporal.ChronoUnit.MILLIS;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.ID;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class MongoLockProviderTest {
    public static final String LOCK_NAME1 = "name";
    private static final LockConfiguration LOCK_CONFIGURATION1 = lockConfig(LOCK_NAME1, 10_000);
    private static final LockConfiguration LOCK_CONFIGURATION2 = lockConfig("name2", 10_000);
    private static final String COLLECTION_NAME = "Shedlock";
    private static final String DB_NAME = "db";
    private final MongoClient mongo = new Fongo("fongo").getMongo();
    private final MongoLockProvider mongoLockProvider = new MongoLockProvider(mongo, DB_NAME, COLLECTION_NAME);

    @Test
    public void shouldCreateLock() {
        Optional<SimpleLock> lock = mongoLockProvider.lock(LOCK_CONFIGURATION1);
        assertThat(lock).isNotEmpty();

        {
            Document lockDocument = getLockDocument(LOCK_NAME1);
            assertThat((Date) lockDocument.get(LOCK_UNTIL)).isAfter(new Date());
            assertThat((Date) lockDocument.get(LOCKED_AT)).isBefore(new Date());
            assertThat((String) lockDocument.get(LOCKED_BY)).isNotEmpty();
        }
        lock.get().unlock();
        {
            Document lockDocument = getLockDocument(LOCK_NAME1);
            assertThat((Date) lockDocument.get(LOCK_UNTIL)).isBefore(new Date());
            assertThat((Date) lockDocument.get(LOCKED_AT)).isBefore(new Date());
            assertThat((String) lockDocument.get(LOCKED_BY)).isNotEmpty();
        }

    }

    @Test
    public void shouldNotReturnSecondLock() {
        Optional<SimpleLock> lock = mongoLockProvider.lock(LOCK_CONFIGURATION1);
        assertThat(lock).isNotEmpty();
        assertThat(mongoLockProvider.lock(LOCK_CONFIGURATION1)).isEmpty();
        lock.get().unlock();
    }

    @Test
    public void shouldCreateTwoIndependentLocks() {
        Optional<SimpleLock> lock1 = mongoLockProvider.lock(LOCK_CONFIGURATION1);
        assertThat(lock1).isNotEmpty();

        Optional<SimpleLock> lock2 = mongoLockProvider.lock(LOCK_CONFIGURATION2);
        assertThat(lock2).isNotEmpty();

        lock1.get().unlock();
        lock2.get().unlock();
    }

    @Test
    public void shouldLockTwiceInARow() {
        Optional<SimpleLock> lock1 = mongoLockProvider.lock(LOCK_CONFIGURATION1);
        assertThat(lock1).isNotEmpty();
        lock1.get().unlock();

        Optional<SimpleLock> lock2 = mongoLockProvider.lock(LOCK_CONFIGURATION1);
        assertThat(lock2).isNotEmpty();
        lock2.get().unlock();
    }

    @Test
    public void shouldTimeout() throws InterruptedException {
        LockConfiguration configWithShortTimeout = lockConfig(LOCK_NAME1, 2);
        Optional<SimpleLock> lock1 = mongoLockProvider.lock(configWithShortTimeout);
        assertThat(lock1).isNotEmpty();

        Thread.sleep(2);

        Optional<SimpleLock> lock2 = mongoLockProvider.lock(configWithShortTimeout);
        assertThat(lock2).isNotEmpty();
    }


    private Document getLockDocument(String lockName) {
        return mongo.getDatabase(DB_NAME).getCollection(COLLECTION_NAME).find(eq(ID, lockName)).first();
    }

    private static LockConfiguration lockConfig(String name, int timeoutMillis) {
        return new LockConfiguration(name, Instant.now().plus(timeoutMillis, MILLIS));
    }

}