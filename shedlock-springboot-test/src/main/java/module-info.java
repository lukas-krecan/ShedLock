open module net.javacrumbs.shedlock.test.springboot {
    requires spring.context;
    requires hsqldb;
    requires HikariCP;
    requires org.slf4j;
    requires spring.jdbc;
    requires net.javacrumbs.shedlock.provider.jdbctemplate;
    requires net.javacrumbs.shedlock.spring;
    requires java.sql;
    requires spring.boot.autoconfigure;
    requires spring.boot;
}