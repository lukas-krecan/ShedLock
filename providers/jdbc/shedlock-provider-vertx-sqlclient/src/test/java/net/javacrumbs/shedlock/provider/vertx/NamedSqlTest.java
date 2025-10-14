package net.javacrumbs.shedlock.provider.vertx;

import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamedSqlTest {

    @Test
    void shouldReplaceParameters() {
        String sql = "test (:name, :lockUntil)";
        NamedSql.Statement statement = new NamedSql(i -> "\\$" + i).translate(sql, Map.of(
            "name", "value1",
            "lockUntil", "value2"
        ));

        assertThat(statement.sql()).isEqualTo("test ($1, $2)");
    }
}
