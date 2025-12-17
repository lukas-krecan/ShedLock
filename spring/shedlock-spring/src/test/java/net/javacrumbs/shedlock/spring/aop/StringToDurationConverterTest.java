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
package net.javacrumbs.shedlock.spring.aop;

import static java.time.Duration.parse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests for {@link StringToDurationConverter} rewritten to parameterized tests.
 */
public class StringToDurationConverterTest {

    @ParameterizedTest
    @MethodSource("iso8601Inputs")
    public void convertWhenIso8601ShouldReturnDuration(String input, Duration expected) {
        assertThat(convert(input)).isEqualTo(expected);
    }

    static Stream<Arguments> iso8601Inputs() {
        return Stream.of(
                args("PT20.345S", parse("PT20.345S")),
                args("PT15M", parse("PT15M")),
                args("+PT15M", parse("PT15M")),
                args("PT10H", parse("PT10H")),
                args("P2D", parse("P2D")),
                args("P2DT3H4M", parse("P2DT3H4M")),
                args("-PT6H3M", parse("-PT6H3M")),
                args("-PT-6H+3M", parse("-PT-6H+3M")));
    }

    @ParameterizedTest
    @MethodSource("simpleInputs")
    public void convertWhenSimpleShouldReturnDuration(String input, Duration expected) {
        assertThat(convert(input)).isEqualTo(expected);
    }

    static Stream<Arguments> simpleInputs() {
        return Stream.of(
                // nanos
                args("10ns", Duration.ofNanos(10)),
                args("10NS", Duration.ofNanos(10)),
                args("+10ns", Duration.ofNanos(10)),
                args("-10ns", Duration.ofNanos(-10)),

                // micros
                args("10us", Duration.ofNanos(10_000)),
                args("10US", Duration.ofNanos(10_000)),
                args("+10us", Duration.ofNanos(10_000)),
                args("-10us", Duration.ofNanos(-10_000)),

                // millis
                args("10ms", Duration.ofMillis(10)),
                args("10MS", Duration.ofMillis(10)),
                args("+10ms", Duration.ofMillis(10)),
                args("-10ms", Duration.ofMillis(-10)),

                // seconds
                args("10s", Duration.ofSeconds(10)),
                args("10S", Duration.ofSeconds(10)),
                args("+10s", Duration.ofSeconds(10)),
                args("-10s", Duration.ofSeconds(-10)),

                // minutes
                args("10m", Duration.ofMinutes(10)),
                args("10M", Duration.ofMinutes(10)),
                args("+10m", Duration.ofMinutes(10)),
                args("-10m", Duration.ofMinutes(-10)),

                // hours
                args("10h", Duration.ofHours(10)),
                args("10H", Duration.ofHours(10)),
                args("+10h", Duration.ofHours(10)),
                args("-10h", Duration.ofHours(-10)),

                // days
                args("10d", Duration.ofDays(10)),
                args("10D", Duration.ofDays(10)),
                args("+10d", Duration.ofDays(10)),
                args("-10d", Duration.ofDays(-10)),

                // default (no suffix -> millis)
                args("10", Duration.ofMillis(10)),
                args("+10", Duration.ofMillis(10)),
                args("-10", Duration.ofMillis(-10)));
    }

    @Test
    public void convertWhenBadFormatShouldThrowException() {
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> convert("10foo"))
                .withMessageContaining("'10foo' is not a valid duration");
    }

    private static Arguments args(String actual, Duration expected) {
        return Arguments.of(actual, expected);
    }

    private static Duration convert(String source) {
        return StringToDurationConverter.INSTANCE.convert(source);
    }
}
