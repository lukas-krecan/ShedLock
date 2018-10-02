/**
 * Copyright 2009-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.test.support.jdbc;

import ru.yandex.qatools.embed.postgresql.PostgresExecutable;
import ru.yandex.qatools.embed.postgresql.PostgresProcess;
import ru.yandex.qatools.embed.postgresql.PostgresStarter;

import java.io.IOException;

class PostgresConfig implements DbConfig {

    private static final String TEST_SCHEMA_NAME = "shedlock_test";
    private final PostgresStarter<PostgresExecutable, PostgresProcess> runtime = PostgresStarter.getDefaultInstance();
    private PostgresProcess process;
    private ru.yandex.qatools.embed.postgresql.config.PostgresConfig config;

    public void startDb() throws IOException {
        config = ru.yandex.qatools.embed.postgresql.config.PostgresConfig.defaultWithDbName(TEST_SCHEMA_NAME, "SA", "pass");
        PostgresExecutable exec = runtime.prepare(config);
        process = exec.start();

    }

    public void shutdownDb() {
        process.stop();
    }

    public String getJdbcUrl() {
        return String.format("jdbc:postgresql://%s:%s/%s?currentSchema=public&user=%s&password=%s",
            config.net().host(),
            config.net().port(),
            config.storage().dbName(),
            config.credentials().username(),
            config.credentials().password()
        );
    }

    @Override
    public String getUsername() {
        return config.credentials().username();
    }

    @Override
    public String getPassword() {
        return config.credentials().password();
    }
}
