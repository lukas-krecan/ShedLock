package net.javacrumbs.shedlock.provider.sql;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import org.junit.jupiter.api.Test;

class DatabaseProductTest {

    @Test
    void shouldMatchDb2() {
        assertThat(DatabaseProduct.matchProductName("dB2 for zOS 456")).isEqualTo(DatabaseProduct.DB2);
    }
}
