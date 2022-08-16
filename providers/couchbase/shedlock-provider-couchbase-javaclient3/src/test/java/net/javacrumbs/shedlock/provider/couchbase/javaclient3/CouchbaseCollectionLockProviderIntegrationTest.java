package net.javacrumbs.shedlock.provider.couchbase.javaclient3;

import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import com.couchbase.client.java.manager.collection.CollectionSpec;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.time.Instant.parse;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient3.CouchbaseLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient3.CouchbaseLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient3.CouchbaseLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class CouchbaseCollectionLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private static final String BUCKET_NAME = "testBucket";

    private static final String COLLECTION_NAME = "testCollection";

    private CouchbaseLockProvider lockProvider;
    private CouchbaseLockConfiguration couchbaseLockConfiguration;
    private static Cluster cluster;
    private static Bucket bucket;
    private static Collection collection;
    private static CouchbaseContainer container;

    @BeforeAll
    public static void startCouchbase () {
        container = new CouchbaseContainer(DockerImageName.parse("couchbase/server").withTag("7.1.1")).withBucket(new BucketDefinition(BUCKET_NAME));
        container.start();

        Set<SeedNode> seedNodes = new HashSet<>(Arrays.asList(
            SeedNode.create(container.getContainerIpAddress(),
                Optional.of(container.getBootstrapCarrierDirectPort()),
                Optional.of(container.getBootstrapHttpDirectPort()))));
        ClusterOptions options = ClusterOptions.clusterOptions(container.getUsername(), container.getPassword());

        cluster = Cluster.connect(seedNodes, options);
        bucket = cluster.bucket(BUCKET_NAME);
        bucket.waitUntilReady(Duration.ofSeconds(30));
        bucket.collections().createCollection(CollectionSpec.create(COLLECTION_NAME, "_default"));
        collection = bucket.collection(COLLECTION_NAME);
    }

    @AfterAll
    public static void stopCouchbase () {
        cluster.disconnect();
        container.stop();
    }

    @BeforeEach
    public void createLockProvider()  {
        couchbaseLockConfiguration = new DefaultCouchbaseLockConfiguration(bucket, collection);
        lockProvider = new CouchbaseLockProvider(couchbaseLockConfiguration);
    }

    @AfterEach
    public void clear() {
        try {
            couchbaseLockConfiguration.collection().remove(LOCK_NAME1);
        } catch (Exception e) {
            // ignore
        }
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    public void assertUnlocked(String lockName) {
        GetResult result = couchbaseLockConfiguration.collection().get(lockName);
        JsonObject lockDocument = result.contentAsObject();

        assertThat(parse((String) lockDocument.get(LOCK_UNTIL))).isBeforeOrEqualTo(now());
        assertThat(parse((String) lockDocument.get(LOCKED_AT))).isBefore(now());
        assertThat(lockDocument.get(LOCKED_BY)).asString().isNotEmpty();
    }

    @Override
    public void assertLocked(String lockName) {
        GetResult result = couchbaseLockConfiguration.collection().get(lockName);
        JsonObject lockDocument = result.contentAsObject();

        assertThat(parse((String) lockDocument.get(LOCK_UNTIL))).isAfter(now());
        assertThat(parse((String) lockDocument.get(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockDocument.get(LOCKED_BY)).asString().isNotEmpty();
    }
}

