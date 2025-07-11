/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.firestore;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.NoCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.support.StorageBasedLockProvider;
import net.javacrumbs.shedlock.test.support.AbstractStorageBasedLockProviderIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.FirestoreEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class FirestoreLockProviderIntegrationTest extends AbstractStorageBasedLockProviderIntegrationTest {
    @Container
    public static final FirestoreEmulatorContainer firestoreEmulator;

    static {
        DockerImageName googleCloudCliImage =
                DockerImageName.parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:425.0.0-emulators");
        firestoreEmulator = new FirestoreEmulatorContainer(googleCloudCliImage)
                .withReuse(true);
    }

    private Firestore firestore;
    private FirestoreLockProvider.Configuration configuration;
    private FirestoreStorageAccessor accessor;
    private FirestoreLockProvider provider;

    @BeforeEach
    void init() {
        this.firestore = FirestoreOptions.newBuilder()
                .setCredentials(NoCredentials.getInstance())
                .setProjectId("shedlock-provider-firestore-test")
                .setHost(firestoreEmulator.getEmulatorEndpoint())
                .build()
                .getService();
        this.configuration = FirestoreLockProvider.Configuration.builder()
                .withFirestore(firestore)
                .withCollectionName("shedlock")
                .withFieldNames(new FirestoreLockProvider.FieldNames("until", "at", "by"))
                .build();
        this.accessor = new FirestoreStorageAccessor(this.configuration);
        this.provider = new FirestoreLockProvider(this.configuration);
    }

    @AfterEach
    void cleanup() {
        try {
            // Clean up all documents in the collection
            firestore.collection(configuration.getCollectionName())
                    .listDocuments()
                    .forEach(docRef -> {
                        try {
                            docRef.delete().get();
                        } catch (Exception e) {
                            // Ignore cleanup errors
                        }
                    });
        } catch (Exception e) {
            // Ignore cleanup errors
        }
    }

    @Override
    protected void assertUnlocked(String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName).orElseThrow();
        assertThat(lock.lockedUntil()).isBefore(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected void assertLocked(String lockName) {
        var now = ClockProvider.now();
        var lock = findLock(lockName).orElseThrow();
        assertThat(lock.lockedUntil()).isAfter(now);
        assertThat(lock.lockedAt()).isBefore(now);
        assertThat(lock.lockedBy()).isNotEmpty();
    }

    @Override
    protected StorageBasedLockProvider getLockProvider() {
        return this.provider;
    }

    Optional<FirestoreStorageAccessor.Lock> findLock(String lockName) {
        return this.accessor.findLock(lockName);
    }
}
