package net.javacrumbs.shedlock.support;

import org.junit.Test;

import java.time.Instant;

import static net.javacrumbs.shedlock.support.Utils.toIsoString;
import static org.assertj.core.api.Assertions.assertThat;


public class UtilsTest {

    @Test
    public void testFormatDate() {
        assertThat(toIsoString(Instant.parse("2018-12-07T12:30:37Z"))).isEqualTo("2018-12-07T12:30:37.000Z");
        assertThat(toIsoString(Instant.parse("2018-12-07T12:30:37.81Z"))).isEqualTo("2018-12-07T12:30:37.810Z");
        assertThat(toIsoString(Instant.parse("2018-12-07T12:30:37.811Z"))).isEqualTo("2018-12-07T12:30:37.811Z");
    }
}