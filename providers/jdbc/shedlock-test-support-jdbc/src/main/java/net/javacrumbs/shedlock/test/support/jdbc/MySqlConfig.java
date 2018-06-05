/**
 * Copyright 2009-2017 the original author or authors.
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

import com.wix.mysql.EmbeddedMysql;
import com.wix.mysql.config.MysqldConfig;
import com.wix.mysql.config.SchemaConfig;

import static com.wix.mysql.EmbeddedMysql.anEmbeddedMysql;
import static com.wix.mysql.config.MysqldConfig.aMysqldConfig;
import static com.wix.mysql.config.SchemaConfig.aSchemaConfig;
import static com.wix.mysql.distribution.Version.v5_6_latest;

class MySqlConfig implements DbConfig {

    private static final String TEST_SCHEMA_NAME = "shedlock_test";
    private static final String USERNAME = "SA";
    private static final String PASSWORD = "";
    private static final SchemaConfig schemaConfig = aSchemaConfig(TEST_SCHEMA_NAME).build();
    private EmbeddedMysql mysqld;

    public void startDb() {
        MysqldConfig config = aMysqldConfig(v5_6_latest)
            .withUser(USERNAME, PASSWORD)
            .build();

        mysqld = anEmbeddedMysql(config)
            .addSchema(schemaConfig)
            .start();
    }

    public void shutdownDb() {
        mysqld.dropSchema(schemaConfig);
        mysqld.stop();
    }

    public String getJdbcUrl() {
        return "jdbc:mysql://localhost:3310/" + TEST_SCHEMA_NAME;
    }

    @Override
    public String getUsername() {
        return USERNAME;
    }

    @Override
    public String getPassword() {
        return PASSWORD;
    }
}
