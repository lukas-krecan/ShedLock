package net.javacrumbs.micronaut.test;

import io.micronaut.runtime.Micronaut;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;

import javax.inject.Singleton;
import javax.sql.DataSource;

public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }

    @Singleton
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource, "myapp.shedlock");
    }
}