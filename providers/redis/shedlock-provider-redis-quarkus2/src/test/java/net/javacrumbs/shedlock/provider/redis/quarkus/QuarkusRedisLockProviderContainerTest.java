package net.javacrumbs.shedlock.provider.redis.quarkus;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import javax.inject.Inject;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class QuarkusRedisLockProviderContainerTest {

    @Inject
    LockedService lockedService;

    @Inject
    RedisDataSource dataSource;

    @Test
    void test_sequenceCalls() {
        lockedService.test();
        lockedService.test();
        assertEquals(2, lockedService.count());
    }
}
