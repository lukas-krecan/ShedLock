/**
 * Copyright 2009-2019 the original author or authors.
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
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.document.JsonDocument;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import static java.time.Instant.now;
import static java.time.Instant.parse;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider.LOCKED_AT;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider.LOCKED_BY;
import static net.javacrumbs.shedlock.provider.couchbase.javaclient.CouchbaseLockProvider.LOCK_UNTIL;
import static org.assertj.core.api.Assertions.assertThat;

public class CouchbaseLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {

    private static final String BUCKET_NAME = "test";
    private static final String HOST = "127.0.0.1";


    private CouchbaseLockProvider lockProvider;
    private static Cluster cluster;
    private static Bucket bucket;

    @BeforeClass
    public static void startCouchbase () {
        cluster = connect();
        cluster.authenticate("Administrator", "password");

        bucket = cluster.openBucket(BUCKET_NAME);
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

    @After
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
        assertThat(parse((String) lockDocument.content().get(LOCK_UNTIL))).isBefore(now());
        assertThat(parse((String) lockDocument.content().get(LOCKED_AT))).isBefore(now());
        assertThat(lockDocument.content().get(LOCKED_BY)).asString().isNotEmpty();
    }

    @Override
    public void assertLocked(String lockName) {
        JsonDocument lockDocument = bucket.get(lockName);
        assertThat(parse((String) lockDocument.content().get(LOCK_UNTIL))).isAfter(now());
        assertThat(parse((String) lockDocument.content().get(LOCKED_AT))).isBefore(now());
        assertThat(lockDocument.content().get(LOCKED_BY)).asString().isNotEmpty();
    }

    private static Cluster connect(){
        return CouchbaseCluster.create(HOST);
    }

    private static void disconnect(Cluster cluster){
        cluster.disconnect();
    }

    private Bucket getBucket(Cluster cluster) {
        return bucket;
    }

}