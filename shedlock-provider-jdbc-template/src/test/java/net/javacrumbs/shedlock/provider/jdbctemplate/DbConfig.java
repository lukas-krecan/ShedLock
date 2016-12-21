package net.javacrumbs.shedlock.provider.jdbctemplate;

import java.io.IOException;

public interface DbConfig {
    void startDb() throws IOException;

    void shutdownDb();

    String getJdbcUrl();

    String getUsername();

    String getPassword();
}
