package net.javacrumbs.shedlock.provider.redis.quarkus;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;

import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import net.javacrumbs.shedlock.cdi.SchedulerLock;

@ApplicationScoped
public class LockedService{
    
    private AtomicInteger count = new AtomicInteger(0);
    
    @SchedulerLock(name = "test")
    public void test(int time) {
        
        execute(time);
        
        Log.info("Executing [DONE]");
        
    }
    
    public void execute(int time) {
        count.incrementAndGet();
        Log.info("Executing ....");
        
        try {
            TimeUnit.MILLISECONDS.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
  
    
    @SchedulerLock(name = "testException")
    @Blocking
    public void testException() {
        
        try {
            TimeUnit.MILLISECONDS.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        
        throw new RuntimeException("test");
        
    }
    
    public int count() {
        return count.get();
    }

    public void countReset() {
        count.set(0);
    }

}
