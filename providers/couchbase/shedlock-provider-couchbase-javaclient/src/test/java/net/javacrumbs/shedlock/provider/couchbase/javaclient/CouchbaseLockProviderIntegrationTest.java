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
package net.javacrumbs.shedlock.provider.couchbase.javaclient;


import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import com.couchbase.client.java.env.CouchbaseEnvironment;
import com.couchbase.client.java.env.DefaultCouchbaseEnvironment;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.couchbase.BucketDefinition;
import org.testcontainers.couchbase.CouchbaseContainer;

import static java.time.Instant.parse;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class CouchbaseLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private static final String BUCKET_NAME = "test";

    private CouchbaseLockProvider lockProvider;
    private static CouchbaseCluster cluster;
    private static Bucket bucket;
    private static CouchbaseContainer container;

    @BeforeAll
    public static void startCouchbase () {
        container = new CouchbaseContainer().withBucket(new BucketDefinition(BUCKET_NAME));
        container.start();

        CouchbaseEnvironment environment = DefaultCouchbaseEnvironment
            .builder()
            .bootstrapCarrierDirectPort(container.getBootstrapCarrierDirectPort())
            .bootstrapHttpDirectPort(container.getBootstrapHttpDirectPort())
            .build();

        cluster = CouchbaseCluster.create(
            environment,
            container.getContainerIpAddress()
        );

        cluster.authenticate(container.getUsername(), container.getPassword());

        bucket = cluster.openBucket(BUCKET_NAME);
    }

    @AfterAll
    public static void stopCouchbase () {
        cluster.disconnect();
        container.stop();
    }

    @BeforeEach
    public void createLockProvider()  {
        bucket = getBucket();
        lockProvider = new CouchbaseLockProvider(bucket);
    }

    @AfterEach
    public void clear() {
        try {
            bucket.remove(LOCK_NAME1);
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
        JsonDocument lockDocument = bucket.get(lockName);
        assertThat(parse((String) lockDocument.content().get(LOCK_UNTIL))).isBeforeOrEqualTo(now());
        assertThat(parse((String) lockDocument.content().get(LOCKED_AT))).isBefore(now());
        assertThat(lockDocument.content().get(LOCKED_BY)).asString().isNotEmpty();
    }

    @Override
    public void assertLocked(String lockName) {
        JsonDocument lockDocument = bucket.get(lockName);
        assertThat(parse((String) lockDocument.content().get(LOCK_UNTIL))).isAfter(now());
        assertThat(parse((String) lockDocument.content().get(LOCKED_AT))).isBeforeOrEqualTo(now());
        assertThat(lockDocument.content().get(LOCKED_BY)).asString().isNotEmpty();
    }

    private Bucket getBucket() {
        return bucket;
    }

}
