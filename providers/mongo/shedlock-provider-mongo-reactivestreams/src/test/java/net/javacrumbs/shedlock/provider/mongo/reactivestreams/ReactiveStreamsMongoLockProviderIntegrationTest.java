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
package net.javacrumbs.shedlock.provider.mongo.reactivestreams;

import static com.mongodb.client.model.Filters.eq;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.ID;
import static net.javacrumbs.shedlock.provider.mongo.reactivestreams.ReactiveStreamsMongoLockProvider.execute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.mongodb.WriteConcern;
import com.mongodb.client.result.DeleteResult;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class ReactiveStreamsMongoLockProviderIntegrationTest
        extends AbstractReactiveStreamsMongoLockProviderIntegrationTest {

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return ReactiveStreamsMongoLockProvider.create(mongo.getDatabase(DB_NAME), false);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldUseSuppliedCollection(boolean useDbTime) {
        var collection =
                mongo.getDatabase(DB_NAME).getCollection("customShedLock").withWriteConcern(WriteConcern.MAJORITY);
        ExtensibleLockProvider provider = ReactiveStreamsMongoLockProvider.create(collection, useDbTime);

        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertThat(execute(collection.find(eq(ID, LOCK_NAME1)).first())).isNotNull();
        assertThat(execute(getLockCollection().countDocuments())).isZero();
    }

    @Test
    public void shouldLockWhenDocumentRemovedExternally() {
        LockProvider provider = getLockProvider();
        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);

        DeleteResult result = requireNonNull(execute(getLockCollection().deleteOne(eq(ID, LOCK_NAME1))));
        assumeThat(result.getDeletedCount()).isEqualTo(1);

        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);
    }
}
