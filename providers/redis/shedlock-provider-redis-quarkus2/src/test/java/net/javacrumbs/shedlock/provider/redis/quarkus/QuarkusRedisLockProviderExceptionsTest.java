package net.javacrumbs.shedlock.provider.redis.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import net.javacrumbs.shedlock.support.LockException;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestProfile(QuarkusRedisLockProviderExceptionsTest.MyProfile.class)
public class QuarkusRedisLockProviderExceptionsTest {

    public static class MyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("shedlock.quarkus.throws-exception-if-locked", "true");
        }
    }

    @Inject
    LockedService lockedService;

    @Inject
    RedisDataSource dataSource;

    private ExecutorService executorService = Executors.newFixedThreadPool(5);
    
    @AfterEach
    public void afterEach() {
        lockedService.countReset();
        try {
            executorService.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    @Order(1)
    void test_warmUp() throws Exception {
        lockedService.test(100);
        assertEquals(1, lockedService.count());
    }
    
    @Test
    void test_sequenceCalls() throws Exception {
        lockedService.test(50);
        lockedService.test(50);
        assertEquals(2, lockedService.count());
    }

    @Test
    void test_basicLock_withExcepions() throws Exception {
        
        executorService.execute(() -> { lockedService.test(300); });
        Thread.sleep(100);
        assertTrue(isLockExist("test"), this::lockMessage);
        
        assertThrows(LockException.class, () -> {
          lockedService.test(300); 
        });
        
        
        Thread.sleep(300); // wait to release lock and verify
        assertFalse(isLockExist("test"), this::lockMessage);
        assertEquals(1, lockedService.count());

    }
    
    
    @Test
    void test_highConcurrency() throws Exception {
        
        for (int i = 0; i < 100; i++) {
            executorService.execute(() -> { lockedService.test(2000); });
            Thread.sleep(10);
        }
        
        assertTrue(isLockExist("test"), this::lockMessage);
        assertEquals(1, lockedService.count());
    }
    
    
    private boolean isLockExist(String name) {
        return dataSource.key().exists("lock:my-app:test:" + name);
    }
    
    private String lockMessage() {
        return "Current Keys: " + dataSource.key().keys("*");
    }

}
