/**
 * Copyright 2009-2020 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.output.OutputFrame;

import java.util.function.Consumer;

public final class OracleServerConfig implements DbConfig {

    private OracleContainer oracle;
    private static final Logger logger = LoggerFactory.getLogger(OracleServerConfig.class);

    public void startDb() {
        oracle = new OracleContainer("oracleinanutshell/oracle-xe-11g")
            .withLogConsumer(new Consumer<OutputFrame>() {
                @Override
                public void accept(OutputFrame outputFrame) {
                    logger.debug(outputFrame.getUtf8String());
                }
            });
        oracle.start();
    }

    public void shutdownDb() {
        oracle.stop();
    }

    public String getJdbcUrl() {
        return oracle.getJdbcUrl();

    }

    @Override
    public String getUsername() {
        return oracle.getUsername();
    }

    @Override
    public String getPassword() {
        return oracle.getPassword();
    }
}
