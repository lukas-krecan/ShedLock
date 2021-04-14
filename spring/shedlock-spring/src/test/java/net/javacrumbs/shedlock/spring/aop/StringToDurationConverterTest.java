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
package net.javacrumbs.shedlock.spring.aop;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link StringToDurationConverter}.
 *
 * @author Phillip Webb
 */
public class StringToDurationConverterTest {

    @Test
    public void convertWhenIso8601ShouldReturnDuration() {
        assertThat(convert("PT20.345S")).isEqualTo(Duration.parse("PT20.345S"));
        assertThat(convert("PT15M")).isEqualTo(Duration.parse("PT15M"));
        assertThat(convert("+PT15M")).isEqualTo(Duration.parse("PT15M"));
        assertThat(convert("PT10H")).isEqualTo(Duration.parse("PT10H"));
        assertThat(convert("P2D")).isEqualTo(Duration.parse("P2D"));
        assertThat(convert("P2DT3H4M")).isEqualTo(Duration.parse("P2DT3H4M"));
        assertThat(convert("-PT6H3M")).isEqualTo(Duration.parse("-PT6H3M"));
        assertThat(convert("-PT-6H+3M")).isEqualTo(Duration.parse("-PT-6H+3M"));
    }

    @Test
    public void convertWhenSimpleNanosShouldReturnDuration() {
        assertThat(convert("10ns")).isEqualTo(Duration.ofNanos(10));
        assertThat(convert("10NS")).isEqualTo(Duration.ofNanos(10));
        assertThat(convert("+10ns")).isEqualTo(Duration.ofNanos(10));
        assertThat(convert("-10ns")).isEqualTo(Duration.ofNanos(-10));
    }

    @Test
    public void convertWhenSimpleMicrosShouldReturnDuration() {
        assertThat(convert("10us")).isEqualTo(Duration.ofNanos(10000));
        assertThat(convert("10US")).isEqualTo(Duration.ofNanos(10000));
        assertThat(convert("+10us")).isEqualTo(Duration.ofNanos(10000));
        assertThat(convert("-10us")).isEqualTo(Duration.ofNanos(-10000));
    }

    @Test
    public void convertWhenSimpleMillisShouldReturnDuration() {
        assertThat(convert("10ms")).isEqualTo(Duration.ofMillis(10));
        assertThat(convert("10MS")).isEqualTo(Duration.ofMillis(10));
        assertThat(convert("+10ms")).isEqualTo(Duration.ofMillis(10));
        assertThat(convert("-10ms")).isEqualTo(Duration.ofMillis(-10));
    }

    @Test
    public void convertWhenSimpleSecondsShouldReturnDuration() {
        assertThat(convert("10s")).isEqualTo(Duration.ofSeconds(10));
        assertThat(convert("10S")).isEqualTo(Duration.ofSeconds(10));
        assertThat(convert("+10s")).isEqualTo(Duration.ofSeconds(10));
        assertThat(convert("-10s")).isEqualTo(Duration.ofSeconds(-10));
    }

    @Test
    public void convertWhenSimpleMinutesShouldReturnDuration() {
        assertThat(convert("10m")).isEqualTo(Duration.ofMinutes(10));
        assertThat(convert("10M")).isEqualTo(Duration.ofMinutes(10));
        assertThat(convert("+10m")).isEqualTo(Duration.ofMinutes(10));
        assertThat(convert("-10m")).isEqualTo(Duration.ofMinutes(-10));
    }

    @Test
    public void convertWhenSimpleHoursShouldReturnDuration() {
        assertThat(convert("10h")).isEqualTo(Duration.ofHours(10));
        assertThat(convert("10H")).isEqualTo(Duration.ofHours(10));
        assertThat(convert("+10h")).isEqualTo(Duration.ofHours(10));
        assertThat(convert("-10h")).isEqualTo(Duration.ofHours(-10));
    }

    @Test
    public void convertWhenSimpleDaysShouldReturnDuration() {
        assertThat(convert("10d")).isEqualTo(Duration.ofDays(10));
        assertThat(convert("10D")).isEqualTo(Duration.ofDays(10));
        assertThat(convert("+10d")).isEqualTo(Duration.ofDays(10));
        assertThat(convert("-10d")).isEqualTo(Duration.ofDays(-10));
    }

    @Test
    public void convertWhenSimpleWithoutSuffixShouldReturnDuration() {
        assertThat(convert("10")).isEqualTo(Duration.ofMillis(10));
        assertThat(convert("+10")).isEqualTo(Duration.ofMillis(10));
        assertThat(convert("-10")).isEqualTo(Duration.ofMillis(-10));
    }

    @Test
    public void convertWhenBadFormatShouldThrowException() {
        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> convert("10foo"))
            .withMessageContaining("'10foo' is not a valid duration");
    }

    private Duration convert(String source) {
        return new StringToDurationConverter().convert(source);
    }

}
