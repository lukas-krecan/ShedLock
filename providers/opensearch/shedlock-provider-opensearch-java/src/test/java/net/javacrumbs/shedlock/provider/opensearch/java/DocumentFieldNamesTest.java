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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

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
}
