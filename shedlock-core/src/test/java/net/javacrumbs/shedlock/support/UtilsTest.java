/**
 * Copyright 2009 the original author or authors.
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
package net.javacrumbs.shedlock.support;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static net.javacrumbs.shedlock.support.Utils.toIsoString;
import static org.assertj.core.api.Assertions.assertThat;


class UtilsTest {

    @Test
    void testFormatDate() {
        assertThat(toIsoString(Instant.parse("2018-12-07T12:30:37Z"))).isEqualTo("2018-12-07T12:30:37.000Z");
        assertThat(toIsoString(Instant.parse("2018-12-07T12:30:37.81Z"))).isEqualTo("2018-12-07T12:30:37.810Z");
        assertThat(toIsoString(Instant.parse("2018-12-07T12:30:37.811Z"))).isEqualTo("2018-12-07T12:30:37.811Z");
    }
}
