package net.javacrumbs.shedlock.provider.mongo;

import com.mongodb.MongoClient;
import de.flapdoodle.embed.mongo.tests.MongodForTestsFactory;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.bson.Document;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.Date;

import static com.mongodb.client.model.Filters.eq;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.ID;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class MongoLockProviderIntegrationIntegrationTest extends AbstractLockProviderIntegrationTest {
    private static MongodForTestsFactory mongoFactory;

    private static final String COLLECTION_NAME = "Shedlock";
    private static final String DB_NAME = "db";
    private MongoLockProvider lockProvider;
    private MongoClient mongo;

    @Before
    public void createLockProvider() throws UnknownHostException {
        mongo = mongoFactory.newMongo();
        lockProvider = new MongoLockProvider(mongo, DB_NAME, COLLECTION_NAME);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
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

    @BeforeClass
    public static void startMongo() throws IOException {
        mongoFactory = new MongodForTestsFactory();
    }

    @AfterClass
    public static void stopMongo() throws IOException {
        mongoFactory.shutdown();
    }
}