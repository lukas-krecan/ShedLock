@SuppressWarnings("module")
module net.javacrumbs.shedlock.provider.sql {
    requires net.javacrumbs.shedlock.core;
    requires static org.jspecify;
    requires org.slf4j;
    // Export provider packages
    exports net.javacrumbs.shedlock.provider.sql to
            net.javacrumbs.shedlock.provider.jdbc.internal,
            net.javacrumbs.shedlock.provider.jdbc,
            net.javacrumbs.shedlock.provider.jdbctemplate,
            net.javacrumbs.shedlock.provider.jdbc.micronaut;
}
