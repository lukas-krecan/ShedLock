module shedlock.springboot.test {
    requires java.sql;
    requires net.javacrumbs.shedlock.core;
    requires net.javacrumbs.shedlock.provider.jdbctemplate;
    requires net.javacrumbs.shedlock.spring;
    requires spring.boot;
    requires spring.boot.autoconfigure;
    requires spring.context;
    requires spring.web;

    opens net.javacrumbs.shedlock.test.boot;
}
