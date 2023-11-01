package net.javacrumbs.shedlock.provider.redis.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import javax.inject.Inject;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

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
