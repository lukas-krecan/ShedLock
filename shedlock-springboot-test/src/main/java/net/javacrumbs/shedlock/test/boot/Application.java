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
import net.javacrumbs.shedlock.spring.SpringLockableTaskSchedulerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@SpringBootApplication
@EnableScheduling
public class Application {

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Application.class);
    }


    @Bean
    public TaskScheduler taskScheduler(LockProvider lockProvider) {
        int poolSize = 10;
        return SpringLockableTaskSchedulerFactory.newLockableTaskScheduler(poolSize, lockProvider);
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
        return datasource;
    }
}
