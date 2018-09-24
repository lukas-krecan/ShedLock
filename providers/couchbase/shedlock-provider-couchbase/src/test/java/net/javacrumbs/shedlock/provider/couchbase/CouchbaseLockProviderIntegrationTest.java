package net.javacrumbs.shedlock.provider.couchbase;


import com.couchbase.client.java.document.JsonDocument;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.couchbase.mock.Bucket;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.assertj.core.api.Assertions;
import org.junit.Before;

import java.time.LocalDateTime;

import static net.javacrumbs.shedlock.provider.couchbase.CouchbaseLockProvider.*;

public class CouchbaseLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest{

    private CouchbaseLockProvider lockProvider;
    private Bucket bucket;

    @Before
    public void createLockProvider()  {
        bucket = new Bucket();
        lockProvider = new CouchbaseLockProvider(bucket);
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    public void assertUnlocked(String lockName) {
        JsonDocument lockDocument = bucket.get(lockName);
        Assertions.assertThat(LocalDateTime.parse((String)lockDocument.content().get(LOCK_UNTIL)).isBefore(LocalDateTime.now()));
        Assertions.assertThat(LocalDateTime.parse((String)lockDocument.content().get(LOCKED_AT)).isBefore(LocalDateTime.now()));
        Assertions.assertThat(!((String) lockDocument.content().get(LOCKED_BY)).isEmpty());
    }

    @Override
    public void assertLocked(String lockName) {

        JsonDocument lockDocument = bucket.get(lockName);
        Assertions.assertThat(LocalDateTime.parse((String) lockDocument.content().get(LOCK_UNTIL)).isAfter(LocalDateTime.now()));
        Assertions.assertThat(LocalDateTime.parse((String) lockDocument.content().get(LOCKED_AT)).isBefore(LocalDateTime.now()));
        Assertions.assertThat(!((String) lockDocument.content().get(LOCKED_BY)).isEmpty());

    }

}