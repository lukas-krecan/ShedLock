module net.javacrumbs.shedlock.provider.jdbc {
    requires transitive net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.jdbc.internal;
    requires org.slf4j;
    requires java.sql;
    exports net.javacrumbs.shedlock.provider.jdbc;
}