package net.javacrumbs.shedlock.provider.redis.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class QuarkusRedisLockProviderIntegrationTest {
    
    @Inject
    private LockedService lockedService;
    
    @Inject
    private RedisDataSource dataSource;
    
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
    void test_basicLock() throws Exception {
        
        executorService.execute(() -> { lockedService.test(300); });
        Thread.sleep(100);
        assertTrue(isLockExist("test"));
        
        executorService.execute(() -> { lockedService.test(300); }); // skip this...
        Thread.sleep(300);
        
        assertFalse(isLockExist("test"));
        assertEquals(1, lockedService.count());
        
    }
    
    @Test
    void test_highConcurrency() throws Exception {
        
        for (int i = 0; i < 100; i++) {
            executorService.execute(() -> { lockedService.test(2000); });
            Thread.sleep(10);
        }
        
        assertTrue(isLockExist("test"));
        assertEquals(1, lockedService.count());
    }
    
    
    @Test
    void test_lockFinished() throws Exception {
        
        executorService.execute(() -> { lockedService.test(100); });
        Thread.sleep(200);
        
        executorService.execute(() -> { lockedService.test(100); });
        Thread.sleep(200);
        
        assertEquals(2, lockedService.count());
        
    }
    
    @Test
    void test_ReleaseLock_onException() throws Exception {
        
        executorService.execute(() -> { lockedService.testException(); });
        Thread.sleep(100);
        
        assertTrue(isLockExist("testException")); // not fired exception
        
        // validate Lock (after exception fired)
        Thread.sleep(500);
        assertFalse(isLockExist("testException"));
        
    }
    
    private boolean isLockExist(String name) {
        
        return dataSource.key().exists("lock:my-app:test:" + name);
        
    }
    

}
