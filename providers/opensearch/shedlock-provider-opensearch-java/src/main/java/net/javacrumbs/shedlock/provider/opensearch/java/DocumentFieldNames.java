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

import static java.util.Objects.requireNonNull;

/**
 * Configurable field names for OpenSearch lock documents.
 *
 * <p><b>Note:</b> This class is mirrored in the Elasticsearch provider module.
 * When making changes, ensure both versions remain synchronized:
 * {@code shedlock-provider-elasticsearch9/src/main/java/.../DocumentFieldNames.java}
 *
 * <p>Provides preset configurations for common naming conventions:
 * <ul>
 *   <li>{@link #DEFAULT} - camelCase (name, lockUntil, lockedAt, lockedBy)
 *   <li>{@link #SNAKE_CASE} - snake_case (name, lock_until, locked_at, locked_by)
 *   <li>{@link #UPPER_SNAKE_CASE} - UPPER_SNAKE_CASE (NAME, LOCK_UNTIL, LOCKED_AT, LOCKED_BY)
 *   <li>{@link #LOWER_CASE} - lowercase (name, lockuntil, lockedat, lockedby)
 * </ul>
 *
 * <p>Example usage with SNAKE_CASE (for JsonpMapper configured with SNAKE_CASE naming):
 * <pre>
 * OpenSearchLockProvider provider = new OpenSearchLockProvider(
 *     OpenSearchLockProvider.Configuration.builder()
 *         .withClient(client)
 *         .withFieldNames(DocumentFieldNames.SNAKE_CASE)
 *         .build()
 * );
 * </pre>
 */
public record DocumentFieldNames(String name, String lockUntil, String lockedAt, String lockedBy) {

    /**
     * Default field names using camelCase convention.
     * This is the original naming used by ShedLock.
     */
    public static final DocumentFieldNames DEFAULT =
            new DocumentFieldNames("name", "lockUntil", "lockedAt", "lockedBy");

    /**
     * Field names using snake_case convention.
     * Use this when your OpenSearchClient is configured with a JsonpMapper using SNAKE_CASE naming strategy.
     */
    public static final DocumentFieldNames SNAKE_CASE =
            new DocumentFieldNames("name", "lock_until", "locked_at", "locked_by");

    /**
     * Field names using UPPER_SNAKE_CASE convention.
     */
    public static final DocumentFieldNames UPPER_SNAKE_CASE =
            new DocumentFieldNames("NAME", "LOCK_UNTIL", "LOCKED_AT", "LOCKED_BY");

    /**
     * Field names using lowercase convention (no separators).
     */
    public static final DocumentFieldNames LOWER_CASE =
            new DocumentFieldNames("name", "lockuntil", "lockedat", "lockedby");

    /**
     * Creates a new DocumentFieldNames instance with custom field names.
     *
     * <p>Field names must:
     * <ul>
     *   <li>Not be null or empty</li>
     *   <li>Contain only alphanumeric characters and underscores</li>
     *   <li>Not start with a number</li>
     * </ul>
     *
     * @param name the field name for the lock name
     * @param lockUntil the field name for the lock expiration timestamp
     * @param lockedAt the field name for the lock acquisition timestamp
     * @param lockedBy the field name for the lock owner hostname
     * @throws NullPointerException if any field name is null
     * @throws IllegalArgumentException if any field name is invalid
     */
    public DocumentFieldNames {
        validateFieldName(name, "name");
        validateFieldName(lockUntil, "lockUntil");
        validateFieldName(lockedAt, "lockedAt");
        validateFieldName(lockedBy, "lockedBy");
    }

    private static void validateFieldName(String fieldName, String parameterName) {
        requireNonNull(fieldName, "'" + parameterName + "' field name cannot be null");
        if (fieldName.trim().isEmpty()) {
            throw new IllegalArgumentException("'" + parameterName + "' field name cannot be empty");
        }
        if (!fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid field name '" + fieldName + "' for " + parameterName
                    + ". Field names must contain only alphanumeric characters and underscores, "
                    + "and cannot start with a number.");
        }
    }
}
