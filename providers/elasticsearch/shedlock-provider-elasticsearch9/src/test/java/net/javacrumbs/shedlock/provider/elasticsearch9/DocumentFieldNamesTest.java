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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentFieldNamesTest {

    @Test
    void shouldCreateDefaultFieldNames() {
        DocumentFieldNames fieldNames = DocumentFieldNames.DEFAULT;

        assertThat(fieldNames.name()).isEqualTo("name");
        assertThat(fieldNames.lockUntil()).isEqualTo("lockUntil");
        assertThat(fieldNames.lockedAt()).isEqualTo("lockedAt");
        assertThat(fieldNames.lockedBy()).isEqualTo("lockedBy");
    }

    @Test
    void shouldCreateSnakeCaseFieldNames() {
        DocumentFieldNames fieldNames = DocumentFieldNames.SNAKE_CASE;

        assertThat(fieldNames.name()).isEqualTo("name");
        assertThat(fieldNames.lockUntil()).isEqualTo("lock_until");
        assertThat(fieldNames.lockedAt()).isEqualTo("locked_at");
        assertThat(fieldNames.lockedBy()).isEqualTo("locked_by");
    }

    @Test
    void shouldCreateUpperSnakeCaseFieldNames() {
        DocumentFieldNames fieldNames = DocumentFieldNames.UPPER_SNAKE_CASE;

        assertThat(fieldNames.name()).isEqualTo("NAME");
        assertThat(fieldNames.lockUntil()).isEqualTo("LOCK_UNTIL");
        assertThat(fieldNames.lockedAt()).isEqualTo("LOCKED_AT");
        assertThat(fieldNames.lockedBy()).isEqualTo("LOCKED_BY");
    }

    @Test
    void shouldCreateLowerCaseFieldNames() {
        DocumentFieldNames fieldNames = DocumentFieldNames.LOWER_CASE;

        assertThat(fieldNames.name()).isEqualTo("name");
        assertThat(fieldNames.lockUntil()).isEqualTo("lockuntil");
        assertThat(fieldNames.lockedAt()).isEqualTo("lockedat");
        assertThat(fieldNames.lockedBy()).isEqualTo("lockedby");
    }

    @Test
    void shouldCreateCustomFieldNames() {
        DocumentFieldNames fieldNames =
                new DocumentFieldNames("custom_name", "custom_lock_until", "custom_locked_at", "custom_locked_by");

        assertThat(fieldNames.name()).isEqualTo("custom_name");
        assertThat(fieldNames.lockUntil()).isEqualTo("custom_lock_until");
        assertThat(fieldNames.lockedAt()).isEqualTo("custom_locked_at");
        assertThat(fieldNames.lockedBy()).isEqualTo("custom_locked_by");
    }

    @Test
    void shouldRejectNullFieldNames() {
        assertThatThrownBy(() -> new DocumentFieldNames(null, "lockUntil", "lockedAt", "lockedBy"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("'name' field name cannot be null");

        assertThatThrownBy(() -> new DocumentFieldNames("name", null, "lockedAt", "lockedBy"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("'lockUntil' field name cannot be null");

        assertThatThrownBy(() -> new DocumentFieldNames("name", "lockUntil", null, "lockedBy"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("'lockedAt' field name cannot be null");

        assertThatThrownBy(() -> new DocumentFieldNames("name", "lockUntil", "lockedAt", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("'lockedBy' field name cannot be null");
    }

    @Test
    void shouldRejectEmptyFieldNames() {
        assertThatThrownBy(() -> new DocumentFieldNames("", "lockUntil", "lockedAt", "lockedBy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'name' field name cannot be empty");

        assertThatThrownBy(() -> new DocumentFieldNames("name", "", "lockedAt", "lockedBy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'lockUntil' field name cannot be empty");

        assertThatThrownBy(() -> new DocumentFieldNames("name", "lockUntil", "   ", "lockedBy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("'lockedAt' field name cannot be empty");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "lock\";", // Script injection attempt with quote and semicolon
                "lock'; ctx._source.delete(); //", // Script injection with comment
                "lock.nested", // Dot notation (could access nested fields)
                "123name", // Starting with number
                "lock-until", // Hyphen (invalid identifier)
                "lock until", // Space
                "lock@field", // Special character @
                "lock$field", // Special character $
                "lock(field)", // Parentheses
                "lock[0]", // Array notation
                "lock\nuntil" // Newline
            })
    void shouldRejectInvalidFieldNamesForScriptInjectionPrevention(String invalidFieldName) {
        assertThatThrownBy(() -> new DocumentFieldNames("name", invalidFieldName, "lockedAt", "lockedBy"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid field name");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "lockUntil", // camelCase
                "lock_until", // snake_case
                "LOCK_UNTIL", // UPPER_SNAKE_CASE
                "lockuntil", // lowercase
                "_lockUntil", // Starting with underscore
                "lock_until_123", // With numbers at end
                "L", // Single character
                "lock123" // Letters followed by numbers
            })
    void shouldAcceptValidFieldNames(String validFieldName) {
        DocumentFieldNames fieldNames = new DocumentFieldNames("name", validFieldName, "lockedAt", "lockedBy");
        assertThat(fieldNames.lockUntil()).isEqualTo(validFieldName);
    }
}
