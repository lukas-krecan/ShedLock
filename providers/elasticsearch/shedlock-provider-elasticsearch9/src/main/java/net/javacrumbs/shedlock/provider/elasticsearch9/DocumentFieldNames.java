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
package net.javacrumbs.shedlock.provider.elasticsearch9;

import static java.util.Objects.requireNonNull;

/**
 * Configurable field names for Elasticsearch lock documents.
 *
 * <p>Use {@link #SNAKE_CASE} when your ElasticsearchClient is configured with a JsonpMapper
 * using SNAKE_CASE naming strategy (fixes <a href="https://github.com/lukas-krecan/ShedLock/issues/2007">Issue #2007</a>).
 *
 * <p>Example usage:
 * <pre>
 * ElasticsearchLockProvider provider = new ElasticsearchLockProvider(
 *     ElasticsearchLockProvider.Configuration.builder()
 *         .withClient(client)
 *         .withFieldNames(DocumentFieldNames.SNAKE_CASE)
 *         .build()
 * );
 * </pre>
 *
 * <p><b>Migration note:</b> Changing field names on an existing index requires
 * data migration. If field names are changed without migration, lock acquisition
 * on existing documents will throw an exception (fail-fast behavior) because the
 * expected fields won't exist. This is intentional to make configuration errors
 * immediately visible rather than silently failing. Options:
 * <ul>
 *   <li>Delete the shedlock index and let it be recreated</li>
 *   <li>Reindex documents with updated field names</li>
 *   <li>Use a new index name via Configuration.builder().withIndex()</li>
 * </ul>
 */
public record DocumentFieldNames(String name, String lockUntil, String lockedAt, String lockedBy) {

    /** Default field names using camelCase (name, lockUntil, lockedAt, lockedBy). */
    public static final DocumentFieldNames DEFAULT =
            new DocumentFieldNames("name", "lockUntil", "lockedAt", "lockedBy");

    /** Field names using snake_case (name, lock_until, locked_at, locked_by). */
    public static final DocumentFieldNames SNAKE_CASE =
            new DocumentFieldNames("name", "lock_until", "locked_at", "locked_by");

    public DocumentFieldNames {
        requireNonNull(name, "name cannot be null");
        requireNonNull(lockUntil, "lockUntil cannot be null");
        requireNonNull(lockedAt, "lockedAt cannot be null");
        requireNonNull(lockedBy, "lockedBy cannot be null");
    }
}
