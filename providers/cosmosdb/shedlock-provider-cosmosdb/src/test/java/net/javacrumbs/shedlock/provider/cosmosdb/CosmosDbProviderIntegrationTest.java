/**
 * Copyright 2009-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.cosmosdb;

import com.azure.data.cosmos.CosmosClient;
import com.azure.data.cosmos.CosmosContainer;
import com.azure.data.cosmos.CosmosItemResponse;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

//Ignore this test because you need to setup a CosmosDB in order to execute integration tests.
@Ignore
public class CosmosDbProviderIntegrationTest extends AbstractLockProviderIntegrationTest {

    private static final String LOCK_GROUP = "test";

    private CosmosContainer container;

    @Before
    public void initConnection() throws IOException {

        Properties p = new Properties();
        p.load(new InputStreamReader(CosmosDbProviderIntegrationTest.class.getClassLoader().getResourceAsStream("config.properties")));
        String endpoint = p.getProperty("endopoint");
        String key = p.getProperty("key");

        CosmosClient client = CosmosClient.builder()
                .endpoint(endpoint)
                .key(key)
                .build();


        container = client.createDatabaseIfNotExists("shedlock")
                .flatMap(response -> response.database().createContainerIfNotExists("locks", "/lockGroup"))
                .flatMap(response -> Mono.just(response.container()))
                .block();

        //to be sure that the test starts without dirty data
        container.getItem(LOCK_NAME1, LOCK_GROUP).delete();
    }

    @Override
    public void fuzzTestShouldPass() throws ExecutionException, InterruptedException {
        //Do nothing because it is very expensive if you are running on a real cloud instance.
    }

    @Test
    public void shouldLockWhenDocumentRemovedExternally() {
        LockProvider provider = getLockProvider();
        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);

        container.getItem(LOCK_NAME1, LOCK_GROUP).delete().block();

        Optional<SimpleLock> secondLock = provider.lock(lockConfig(LOCK_NAME1));
        assertThat(secondLock).isNotEmpty();
        assertLocked(LOCK_NAME1);
        secondLock.get().unlock();
        assertUnlocked(LOCK_NAME1);
    }

    @Override
    protected LockProvider getLockProvider() {
        return new CosmosDBProvider(container, LOCK_GROUP);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        Lock lockDocument = getLockDocument(lockName, LOCK_GROUP);
        assertThat(lockDocument.getLockUntil()).isBeforeOrEqualsTo(now());
        assertThat(lockDocument.getLockedAt()).isBeforeOrEqualsTo(now());
        assertThat(lockDocument.getLockedBy()).isNotEmpty();
    }


    @Override
    protected void assertLocked(String lockName) {
        Lock lockDocument = getLockDocument(lockName, LOCK_GROUP);
        assertThat(lockDocument.getLockUntil()).isAfter(now());
        assertThat(lockDocument.getLockedAt()).isBeforeOrEqualsTo(now());
        assertThat(lockDocument.getLockedBy()).isNotEmpty();
    }


    private Date now() {
        return new Date();
    }


    private Lock getLockDocument(String id, String lockGroup) {
        return container.getItem(id, lockGroup)
                .read()
                .map(response -> getObject(response))
                .block();


    }

    private Lock getObject(CosmosItemResponse response) {
        try {
            return response.properties().getObject(Lock.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}