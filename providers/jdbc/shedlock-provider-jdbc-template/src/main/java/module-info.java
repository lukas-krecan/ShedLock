module net.javacrumbs.shedlock.provider.jdbctemplate {
    exports net.javacrumbs.shedlock.provider.jdbctemplate;
    requires net.javacrumbs.shedlock.core;
    requires spring.jdbc;
    requires spring.tx;
    requires java.sql;
    requires org.slf4j;
}
