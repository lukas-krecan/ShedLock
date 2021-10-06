/**
 * Copyright 2009 the original author or authors.
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
import org.testcontainers.containers.JdbcDatabaseContainer;

abstract class AbstractContainerBasedDbConfig<T extends JdbcDatabaseContainer<T>> extends AbstractDbConfig {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    protected final T container;

    public AbstractContainerBasedDbConfig(T container) {
        this.container = container
            .withLogConsumer(outputFrame -> logger.debug(outputFrame.getUtf8String()));
    }

    @Override
    protected final void doStartDb() {
        container.start();
    }

    @Override
    protected final void doShutdownDb() {
        container.stop();
    }

    @Override
    public String getJdbcUrl() {
        return container.getJdbcUrl();
    }

    @Override
    public String getUsername() {
        return container.getUsername();
    }

    @Override
    public String getPassword() {
        return container.getPassword();
    }
}
