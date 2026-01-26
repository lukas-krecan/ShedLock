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
package net.javacrumbs.shedlock.provider.opensearch.java;

/**
 * Configurable field names for OpenSearch lock documents.
 *
 * <p>Use {@link #SNAKE_CASE} when your OpenSearchClient is configured with a JsonpMapper
 * using SNAKE_CASE naming strategy (fixes <a href="https://github.com/lukas-krecan/ShedLock/issues/2007">Issue #2007</a>).
 *
 * <p>Example usage:
 * <pre>
 * OpenSearchLockProvider provider = new OpenSearchLockProvider(
 *     OpenSearchLockProvider.Configuration.builder(client)
 *         .withFieldNames(DocumentFieldNames.SNAKE_CASE)
 *         .build()
 * );
 * </pre>
 *
 * <p><b>Migration note:</b> Changing field names on an existing index requires
 * data migration. If field names are changed without migration, lock acquisition
 * on existing documents will throw an exception (fail-fast behavior).
 * The exception's cause contains details like (example shown for DEFAULT field names):
 * <pre>"Field 'lockUntil' is missing or not a Number. Possible field name mismatch..."</pre>
 *
 * <p><b>Warning:</b> If your index contains documents created with different field-name settings,
 * some documents may have mixed fields (e.g., both {@code lock_until} and {@code lockUntil}).
 * This requires cleanup before switching configurations.
 *
 * <p><b>Diagnosing field name issues:</b> Check current document fields with:
 * <pre>GET /shedlock/_search?size=1</pre>
 * If you see {@code lock_until} but configured {@link #DEFAULT}, or {@code lockUntil}
 * but configured {@link #SNAKE_CASE}, there's a mismatch.
 *
 * <p><b>Resolution options:</b>
 * <ul>
 *   <li><b>Easiest:</b> Delete the shedlock index and let it be recreated:
 *       {@code DELETE /shedlock}</li>
 *   <li><b>Zero-downtime:</b> Use a new index name via
 *       {@code Configuration.builder().withIndex("shedlock_v2")}</li>
 *   <li><b>In-place:</b> Reindex with field name transformation using OpenSearch's
 *       Reindex API with a script</li>
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
        requireHasText(name, "name");
        requireHasText(lockUntil, "lockUntil");
        requireHasText(lockedAt, "lockedAt");
        requireHasText(lockedBy, "lockedBy");
        requireDistinct(name, lockUntil, lockedAt, lockedBy);
    }

    private static void requireHasText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DocumentFieldNames." + field + " must not be blank");
        }
    }

    private static void requireDistinct(String... values) {
        long distinct = java.util.Arrays.stream(values).distinct().count();
        if (distinct != values.length) {
            throw new IllegalArgumentException("DocumentFieldNames must contain distinct field names");
        }
    }
}
