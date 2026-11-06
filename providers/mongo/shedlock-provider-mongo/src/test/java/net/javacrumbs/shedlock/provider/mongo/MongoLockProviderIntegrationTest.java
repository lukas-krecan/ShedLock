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
package net.javacrumbs.shedlock.provider.mongo;

import static com.mongodb.client.model.Filters.eq;
import static net.javacrumbs.shedlock.provider.mongo.MongoLockProvider.ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

import com.mongodb.client.result.DeleteResult;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.Test;

public class MongoLockProviderIntegrationTest extends AbstractMongoLockProviderIntegrationTest {

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return MongoLockProvider.create(getMongo().getDatabase(DB_NAME), false);
    }

    @Test
    public void shouldLockWhenDocumentRemovedExternally() {
        LockProvider provider = getLockProvider();
        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);

        DeleteResult result = getLockCollection().deleteOne(eq(ID, LOCK_NAME1));
        assumeThat(result.getDeletedCount()).isEqualTo(1);

        assertThat(provider.lock(lockConfig(LOCK_NAME1))).isNotEmpty();
        assertLocked(LOCK_NAME1);
    }
}
