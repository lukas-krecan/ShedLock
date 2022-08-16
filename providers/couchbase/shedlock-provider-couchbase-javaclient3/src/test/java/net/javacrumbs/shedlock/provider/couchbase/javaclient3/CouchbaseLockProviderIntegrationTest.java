/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.couchbase.javaclient3;

import com.couchbase.client.core.env.SeedNode;
import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ClusterOptions;
import com.couchbase.client.java.Collection;
import com.couchbase.client.java.json.JsonObject;
import com.couchbase.client.java.kv.GetResult;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;

import java.time.Duration;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.time.Instant.parse;
import static java.util.Collections.singletonList;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient3.CouchbaseLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient3.CouchbaseLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient3.CouchbaseLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class CouchbaseLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private static final String BUCKET_NAME = "test";

    private CouchbaseLockProvider lockProvider;
    private static Cluster cluster;
    private static Bucket bucket;
    private static Collection collection;
    private static CouchbaseContainer container;

    @BeforeAll
    public static void startCouchbase () {
        container = new CouchbaseContainer().withBucket(new BucketDefinition(BUCKET_NAME));
        container.start();

        Set<SeedNode> seedNodes = new HashSet<>(singletonList(
            SeedNode.create(container.getContainerIpAddress(),
                Optional.of(container.getBootstrapCarrierDirectPort()),
                Optional.of(container.getBootstrapHttpDirectPort()))));
        ClusterOptions options = ClusterOptions.clusterOptions(container.getUsername(), container.getPassword());

        cluster = Cluster.connect(seedNodes, options);
        bucket = cluster.bucket(BUCKET_NAME);
        bucket.waitUntilReady(Duration.ofSeconds(30));
        collection = bucket.defaultCollection();
    }

    @AfterAll
    public static void stopCouchbase () {
        cluster.disconnect();
        container.stop();
    }

    @BeforeEach
    public void createLockProvider()  {
        lockProvider = new CouchbaseLockProvider(bucket.defaultCollection());
    }

    @AfterEach
    public void clear() {
        try {
            collection.remove(LOCK_NAME1);
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
        GetResult result = collection.get(lockName);
        JsonObject lockDocument = result.contentAsObject();

        assertThat(parse((String) lockDocument.get(LOCK_UNTIL))).isBeforeOrEqualTo(now());
        assertThat(parse((String) lockDocument.get(LOCKED_AT))).isBefore(now());
        assertThat(lockDocument.get(LOCKED_BY)).asString().isNotEmpty();
    }

    @Override
    public void assertLocked(String lockName) {
        GetResult result = collection.get(lockName);
        JsonObject lockDocument = result.contentAsObject();

        assertThat(parse((String) lockDocument.get(LOCK_UNTIL))).isAfter(now());
        assertThat(parse((String) lockDocument.get(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockDocument.get(LOCKED_BY)).asString().isNotEmpty();
    }
}
