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

import java.util.Date;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import org.bson.BsonTimestamp;
import org.bson.Document;

class DbTimeMongoLockProviderIntegrationTest extends AbstractMongoLockProviderIntegrationTest {

    @Override
    protected ExtensibleLockProvider getLockProvider() {
        return new DbTimeMongoLockProvider(getMongo().getDatabase(DB_NAME));
    }

    @Override
    protected Date getDate(Document lockDocument, String fieldName) {
        Object value = lockDocument.get(fieldName);
        if (value instanceof Date) {
            return (Date) value;
        } else if (value instanceof BsonTimestamp) {
            return new Date(((BsonTimestamp) value).getTime());
        } else {
            throw new UnsupportedOperationException(
                    "Unsupported type: " + value.getClass().getName());
        }
    }
}
