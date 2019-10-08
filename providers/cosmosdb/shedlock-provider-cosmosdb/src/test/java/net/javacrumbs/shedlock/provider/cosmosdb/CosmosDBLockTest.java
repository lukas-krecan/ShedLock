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
package net.javacrumbs.shedlock.provider.cosmosdb;

import com.azure.data.cosmos.CosmosContainer;
import com.azure.data.cosmos.CosmosItem;
import com.azure.data.cosmos.CosmosItemResponse;
import net.javacrumbs.shedlock.core.LockConfiguration;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class CosmosDBLockTest {

    @Test
    public void unlockTest(){
        LockConfiguration lockConfiguration = mock(LockConfiguration.class);
        CosmosContainer container = mock(CosmosContainer.class);
        CosmosItem cosmosItem = mock(CosmosItem.class);
        CosmosItemResponse cosmosItemResponse = mock(CosmosItemResponse.class);
        String hostname = "host";
        String lockGroup = "group";
        Instant unlockTime = Instant.now();
        Instant lockAtLeastUntil = Instant.now();

        when(lockConfiguration.getName()).thenReturn("test");
        when(lockConfiguration.getUnlockTime()).thenReturn(unlockTime);
        when(lockConfiguration.getLockAtLeastUntil()).thenReturn(lockAtLeastUntil);
        when(container.getItem("test", lockGroup)).thenReturn(cosmosItem);

        Lock unlocked = new Lock(lockConfiguration.getName(), Date.from(unlockTime), Date.from(lockAtLeastUntil), hostname, lockGroup);
        when(cosmosItem.replace(unlocked)).thenReturn(Mono.just(cosmosItemResponse));

        CosmosDBLock cosmosDBLock = new CosmosDBLock(container, lockConfiguration, lockGroup, hostname);
        cosmosDBLock.unlock();

        verify(cosmosItem, times(1)).replace(unlocked);
    }

    @Test
    public void hashCodeTest(){
        LockConfiguration lockConfiguration = mock(LockConfiguration.class);
        CosmosDBLock cosmosDBLock = new CosmosDBLock(null, lockConfiguration, "group", "host");
        assertThat(cosmosDBLock.hashCode()).isEqualTo(Objects.hash(lockConfiguration, "group", "host"));
    }

    @Test
    public void equalsTestSuccess(){
        LockConfiguration lockConfiguration = mock(LockConfiguration.class);
        CosmosDBLock cosmosDBLock = new CosmosDBLock(null, lockConfiguration, "group", "host");
        CosmosDBLock other = new CosmosDBLock(null, lockConfiguration, "group", "host");
        assertThat(cosmosDBLock.equals(other)).isTrue();
    }

    @Test
    public void equalsTestFail(){
        LockConfiguration lockConfiguration = mock(LockConfiguration.class);
        CosmosDBLock cosmosDBLock = new CosmosDBLock(null, lockConfiguration, "group", "host");
        CosmosDBLock other0 = new CosmosDBLock(null, lockConfiguration, "groupB", "host");
        assertThat(cosmosDBLock.equals(other0)).isFalse();
        CosmosDBLock other1 = new CosmosDBLock(null, lockConfiguration, "group", "hostB");
        assertThat(cosmosDBLock.equals(other1)).isFalse();
        CosmosDBLock other2 = new CosmosDBLock(null, mock(LockConfiguration.class), "group", "host");
        assertThat(cosmosDBLock.equals(other2)).isFalse();
    }

}
