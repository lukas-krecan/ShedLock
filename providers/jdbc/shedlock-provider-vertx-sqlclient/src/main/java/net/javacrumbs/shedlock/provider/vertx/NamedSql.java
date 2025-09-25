package net.javacrumbs.shedlock.provider.vertx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

class NamedSql {
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":[a-zA-Z]+");

    static Statement translate(String namedSql, Map<String, Object> namedParameters) {
        List<Object> parameters = new ArrayList<>();
        String sql = NAMED_PARAMETER_PATTERN.matcher(namedSql).replaceAll(result -> {
            String key = result.group().substring(1);
            if (!namedParameters.containsKey(key)) {
                throw new IllegalStateException("Parameter " + key + " not found");
            }
            parameters.add(namedParameters.get(key));
            return "?";
        });
        return new Statement(sql, Collections.unmodifiableList(parameters));
    }

    record Statement(String sql, List<Object> parameters) {}
}
