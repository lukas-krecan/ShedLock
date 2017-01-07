package net.javacrumbs.shedlock.test.support.jdbc;

import java.io.IOException;

public interface DbConfig {
    void startDb() throws IOException;

    void shutdownDb();

    String getJdbcUrl();

    String getUsername();

    String getPassword();
}
