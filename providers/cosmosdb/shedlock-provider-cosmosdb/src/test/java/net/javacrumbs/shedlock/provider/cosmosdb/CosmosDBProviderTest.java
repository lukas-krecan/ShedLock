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

import com.azure.data.cosmos.*;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class CosmosDBProviderTest {

    public static final String LOCK_GROUP = "test";
    private CosmosContainer container;
    private LockConfiguration lockConfiguration;
    private CosmosStoredProcedure cosmosStoredProcedure;
    private CosmosStoredProcedureResponse cosmosStoredProcedureResponse;


    @Test
    public void lockTestSuccess() {
        when(cosmosStoredProcedureResponse.responseAsString()).thenReturn("false");
        when(cosmosStoredProcedure.execute(any(Object[].class), any(CosmosStoredProcedureRequestOptions.class))).thenReturn(Mono.just(cosmosStoredProcedureResponse));

        CosmosDBProvider cosmosDBProvider = new CosmosDBProvider(container, LOCK_GROUP);
        Optional<SimpleLock> underTest = cosmosDBProvider.lock(lockConfiguration);

        assertThat(underTest).isNotEmpty();

        assertThat(underTest.get()).isEqualTo(new CosmosDBLock(container, lockConfiguration, LOCK_GROUP, Utils.getHostname()));

        verify(cosmosStoredProcedure, times(1)).execute(any(Object[].class), any(CosmosStoredProcedureRequestOptions.class));
    }

    @Test
    public void lockTestFail() {
        when(cosmosStoredProcedureResponse.responseAsString()).thenReturn("true");
        when(cosmosStoredProcedure.execute(any(Object[].class), any(CosmosStoredProcedureRequestOptions.class))).thenReturn(Mono.just(cosmosStoredProcedureResponse));

        CosmosDBProvider cosmosDBProvider = new CosmosDBProvider(container, LOCK_GROUP);
        Optional<SimpleLock> underTest = cosmosDBProvider.lock(lockConfiguration);

        assertThat(underTest).isEmpty();
        verify(cosmosStoredProcedure, times(1)).execute(any(Object[].class), any(CosmosStoredProcedureRequestOptions.class));
    }

    @Test
    public void getProcedureParamsTest() {
        CosmosDBProvider cosmosDBProvider = new CosmosDBProvider(container, LOCK_GROUP);
        Object[] procedureParams = cosmosDBProvider.getProcedureParams(this.lockConfiguration, 0);
        assertThat(procedureParams).isEqualTo(new Object[]{"name", 1570543520L, 0L, Utils.getHostname(), LOCK_GROUP});
    }

    @Before
    public void mockSetup() {
        container = mock(CosmosContainer.class);
        lockConfiguration = mock(LockConfiguration.class);
        CosmosScripts cosmosScripts = mock(CosmosScripts.class);
        cosmosStoredProcedure = mock(CosmosStoredProcedure.class);
        cosmosStoredProcedureResponse = mock(CosmosStoredProcedureResponse.class);

        when(lockConfiguration.getName()).thenReturn("name");
        when(lockConfiguration.getLockAtMostUntil()).thenReturn(Instant.ofEpochMilli(1570543520));
        when(container.getScripts()).thenReturn(cosmosScripts);
        when(cosmosScripts.getStoredProcedure(CosmosDBProvider.ACQUIRE_LOCK_STORED_PROCEDURE)).thenReturn(cosmosStoredProcedure);
    }

}
