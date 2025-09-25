module net.javacrumbs.shedlock.provider.sql {
    requires net.javacrumbs.shedlock.core;
    // Export provider packages
    exports net.javacrumbs.shedlock.provider.sql to
            net.javacrumbs.shedlock.provider.jdbc.internal,
            net.javacrumbs.shedlock.provider.jdbc,
            net.javacrumbs.shedlock.provider.jdbctemplate,
            net.javacrumbs.shedlock.provider.jdbc.micronaut;
}
