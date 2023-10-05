package net.javacrumbs.shedlock.provider.redis.quarkus2;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.redis.datasource.RedisDataSource;
import net.javacrumbs.shedlock.core.LockProvider;


@ApplicationScoped
public class SchedulerLockFactory {
    
    @ConfigProperty(name = "quarkus.application.name")
    String app;
    
    @ConfigProperty(name = "shedlock.quarkus.throws-exception-if-locked", defaultValue = "false")
    boolean throwsException;
    
    @Produces
    @Singleton
    public LockProvider lockProvider(RedisDataSource redisDataSource) {
        
        return new QuarkusRedisLockProvider(redisDataSource, app, throwsException);
    }
}
