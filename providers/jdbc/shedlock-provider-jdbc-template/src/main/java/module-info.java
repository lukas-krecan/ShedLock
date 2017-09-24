module net.javacrumbs.shedlock.provider.jdbctemplate {
    requires transitive net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.jdbc.internal;
    requires java.sql;
    requires spring.jdbc;
    requires spring.tx;
    requires org.slf4j;
    exports net.javacrumbs.shedlock.provider.jdbctemplate;
}