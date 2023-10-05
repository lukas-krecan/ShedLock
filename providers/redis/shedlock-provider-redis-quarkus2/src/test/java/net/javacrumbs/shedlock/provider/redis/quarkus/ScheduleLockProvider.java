package net.javacrumbs.shedlock.provider.redis.quarkus;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Alternative;
import javax.enterprise.inject.Produces;
import javax.inject.Singleton;

import io.quarkus.arc.Priority;
import io.quarkus.redis.datasource.RedisDataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.quarkus2.QuarkusRedisLockProvider;


@ApplicationScoped
public class ScheduleLockProvider {
    
    @Produces
    @Singleton
    @Alternative()
    @Priority(1)
    public LockProvider lockProvider(RedisDataSource redisDataSource) {
        return new QuarkusRedisLockProvider(redisDataSource, "my-app");
    }
}
