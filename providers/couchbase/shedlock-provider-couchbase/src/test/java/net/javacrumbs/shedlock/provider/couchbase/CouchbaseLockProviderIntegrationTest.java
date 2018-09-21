package net.javacrumbs.shedlock.provider.couchbase;


import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.assertj.core.api.Assertions;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.time.LocalDateTime;

import static net.javacrumbs.shedlock.provider.couchbase.CouchbaseLockProvider.*;

public class CouchbaseLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest{

    private static final String BUCKET_NAME = "bucket_1";
    private static final String HOST = "127.0.0.1";


    private CouchbaseLockProvider lockProvider;
    private Bucket bucket;
    private static Cluster cluster;

    @BeforeClass
    public static void startCouchbase () {
        cluster = connect();
    }

    @AfterClass
    public static void stopCouchbase () {
        disconnect(cluster);
    }

    @Before
    public void createLockProvider()  {
        bucket = getBucket(cluster);
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

    private static Cluster connect(){
        return CouchbaseCluster.create(HOST);
    }

    private static void disconnect(Cluster cluster){
        cluster.disconnect();
    }

    private Bucket getBucket(Cluster cluster) {
        cluster.authenticate(BUCKET_NAME, BUCKET_NAME);
        Bucket bucket = cluster.openBucket(BUCKET_NAME);

        return bucket;
    }

}