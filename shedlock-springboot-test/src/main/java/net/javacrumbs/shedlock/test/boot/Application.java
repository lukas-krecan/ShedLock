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
package net.javacrumbs.shedlock.test.boot;

import com.github.fakemongo.Fongo;
import com.mongodb.MongoClient;
import com.zaxxer.hikari.HikariDataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.ScheduledLockConfiguration;
import net.javacrumbs.shedlock.spring.ScheduledLockConfigurationBuilder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class);
    }

    @Bean
    public ScheduledLockConfiguration taskScheduler(ScheduledExecutorService executorService, LockProvider lockProvider) {
        return ScheduledLockConfigurationBuilder
                .withLockProvider(lockProvider)
                .withExecutorService(executorService)
                .withDefaultLockAtMostFor(Duration.ofMinutes(10))
                .build();
    }

    //    @Bean
//    public LockProvider lockProvider(MongoClient mongo) {
//        return new MongoLockProvider(mongo, "databaseName");
//    }
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource, "shedlock");
    }

    @Bean
    public MongoClient mongo() {
        return new Fongo("fongo").getMongo();
    }

    @Bean
    public DataSource dataSource() {
        HikariDataSource datasource = new HikariDataSource();
        datasource.setJdbcUrl("jdbc:hsqldb:mem:mymemdb");
        datasource.setUsername("SA");
        datasource.setPassword("");

        new JdbcTemplate(datasource).execute("CREATE TABLE shedlock(\n" +
            "    name VARCHAR(64), \n" +
            "    lock_until TIMESTAMP(3) NULL, \n" +
            "    locked_at TIMESTAMP(3) NULL, \n" +
            "    locked_by  VARCHAR(255), \n" +
            "    PRIMARY KEY (name)\n" +
            ")");
        return datasource;
    }
}
