package net.javacrumbs.shedlock.provider.mongo;

import com.github.fakemongo.Fongo;
import com.mongodb.MongoClient;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderTest;
import org.bson.Document;

import java.util.Date;

import static com.mongodb.client.model.Filters.eq;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.ID;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class MongoLockProviderTest extends AbstractLockProviderTest {
    private static final String COLLECTION_NAME = "Shedlock";
    private static final String DB_NAME = "db";
    private final MongoClient mongo = new Fongo("fongo").getMongo();
    private final MongoLockProvider mongoLockProvider = new MongoLockProvider(mongo, DB_NAME, COLLECTION_NAME);


    @Override
    protected LockProvider getLockProvider() {
        return mongoLockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Document lockDocument = getLockDocument(lockName);
        assertThat((Date) lockDocument.get(LOCK_UNTIL)).isBefore(new Date());
        assertThat((Date) lockDocument.get(LOCKED_AT)).isBefore(new Date());
        assertThat((String) lockDocument.get(LOCKED_BY)).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        Document lockDocument = getLockDocument(lockName);
        assertThat((Date) lockDocument.get(LOCK_UNTIL)).isAfter(new Date());
        assertThat((Date) lockDocument.get(LOCKED_AT)).isBefore(new Date());
        assertThat((String) lockDocument.get(LOCKED_BY)).isNotEmpty();
    }

    private Document getLockDocument(String lockName) {
        return mongo.getDatabase(DB_NAME).getCollection(COLLECTION_NAME).find(eq(ID, lockName)).first();
    }
}