package net.javacrumbs.shedlock.provider.couchbase;


import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.bucket.BucketType;
import com.couchbase.client.java.cluster.DefaultBucketSettings;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.query.N1qlParams;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.query.consistency.ScanConsistency;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.testcontainers.couchbase.CouchbaseContainer;

import java.time.LocalDateTime;

import static net.javacrumbs.shedlock.provider.couchbase.CouchbaseLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.couchbase.CouchbaseLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.couchbase.CouchbaseLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.couchbase.AbstractCouchbaseTest.DEFAULT_PASSWORD;
import static org.testcontainers.couchbase.AbstractCouchbaseTest.TEST_BUCKET;

public class CouchbaseLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest{
    @Rule
    public CouchbaseContainer couchbase = new CouchbaseContainer()
        .withNewBucket(DefaultBucketSettings.builder()
            .enableFlush(true)
            .name(TEST_BUCKET)
            .password(DEFAULT_PASSWORD)
            .quota(100)
            .type(BucketType.COUCHBASE)
            .build());

    private static CouchbaseContainer couchbaseContainer;

    private static Bucket bucket;

    @BeforeClass
    public static void setUpCouchbase() {
        couchbaseContainer = initCouchbaseContainer();
        bucket = openBucket(TEST_BUCKET, DEFAULT_PASSWORD);
    }

    @After
    public void clear() {
        if (couchbaseContainer.isIndex() && couchbaseContainer.isQuery() && couchbaseContainer.isPrimaryIndex()) {
            bucket.query(
                N1qlQuery.simple(String.format("DELETE FROM `%s`", bucket.name()),
                    N1qlParams.build().consistency(ScanConsistency.STATEMENT_PLUS)));
        } else {
            bucket.bucketManager().flush();
        }
    }

    @Override
    protected LockProvider getLockProvider() {
        return new CouchbaseLockProvider(bucket);
    }

    @Override
    public void assertUnlocked(String lockName) {
        JsonDocument lockDocument = bucket.get(lockName);
        assertThat(LocalDateTime.parse((String) lockDocument.content().get(LOCK_UNTIL))).isBefore(LocalDateTime.now());
        assertThat(LocalDateTime.parse((String) lockDocument.content().get(LOCKED_AT))).isBefore(LocalDateTime.now());
        assertThat(lockDocument.content().get(LOCKED_BY)).asString().isEmpty();
    }

    @Override
    public void assertLocked(String lockName) {

        JsonDocument lockDocument = bucket.get(lockName);
        assertThat(LocalDateTime.parse((String) lockDocument.content().get(LOCK_UNTIL))).isAfter(LocalDateTime.now());
        assertThat(LocalDateTime.parse((String) lockDocument.content().get(LOCKED_AT))).isBefore(LocalDateTime.now());
        assertThat(lockDocument.content().get(LOCKED_BY)).asString().isEmpty();

    }

    private static Bucket openBucket(String bucketName, String password) {
        CouchbaseCluster cluster = couchbaseContainer.getCouchbaseCluster();
        Bucket bucket = cluster.openBucket(bucketName, password);
        Runtime.getRuntime().addShutdownHook(new Thread(bucket::close));
        return bucket;
    }

    private static CouchbaseContainer initCouchbaseContainer() {
        CouchbaseContainer couchbaseContainer = new CouchbaseContainer()
            .withNewBucket(DefaultBucketSettings.builder()
                .enableFlush(true)
                .name(TEST_BUCKET)
                .password(DEFAULT_PASSWORD)
                .quota(100)
                .replicas(0)
                .type(BucketType.COUCHBASE)
                .build());
        couchbaseContainer.start();
        return couchbaseContainer;
    }
}