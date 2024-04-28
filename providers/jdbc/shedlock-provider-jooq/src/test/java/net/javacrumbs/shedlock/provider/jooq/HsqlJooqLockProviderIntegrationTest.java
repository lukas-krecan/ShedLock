/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.jooq;

import net.javacrumbs.shedlock.test.support.jdbc.DbConfig;
import net.javacrumbs.shedlock.test.support.jdbc.HsqlConfig;
import org.jooq.SQLDialect;
import org.jooq.conf.MappedSchema;
import org.jooq.conf.MappedTable;
import org.jooq.conf.RenderMapping;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;

public class HsqlJooqLockProviderIntegrationTest extends AbstractJooqLockProviderIntegrationTest {
    private static final DbConfig dbConfig = new HsqlConfig();

    public HsqlJooqLockProviderIntegrationTest() {
        super(
                dbConfig,
                DSL.using(
                        dbConfig.getDataSource(),
                        SQLDialect.HSQLDB,
                        new Settings().withRenderNameCase(RenderNameCase.UPPER).withRenderMapping(new RenderMapping()
                            .withSchemata(
                                new MappedSchema().withInput("") //Default schema
                                    .withOutput("orchestrator") //My target schema where I want the shedlock table to be used from
                                    .withTables(
                                        new MappedTable().withInput("shedlock")
                                            .withOutput("shedlock"))))));
    }
}
