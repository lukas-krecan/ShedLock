package net.javacrumbs.shedlock.provider.vertx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

class NamedSql {
    private static final Pattern NAMED_PARAMETER_PATTERN = Pattern.compile(":[a-zA-Z]+");

    static Statement translate(String namedSql, Map<String, Object> namedParameters) {
        List<Object> parameters = new ArrayList<>();
        AtomicInteger counter = new AtomicInteger(0);
        String sql = NAMED_PARAMETER_PATTERN.matcher(namedSql).replaceAll(result -> {
            String key = result.group().substring(1);
            if (!namedParameters.containsKey(key)) {
                throw new IllegalStateException("Parameter " + key + " not found");
            }
            parameters.add(namedParameters.get(key));
            return "\\$" + counter.incrementAndGet();
        });
        return new Statement(sql, Collections.unmodifiableList(parameters));
    }

    record Statement(String sql, List<Object> parameters) {}
}
