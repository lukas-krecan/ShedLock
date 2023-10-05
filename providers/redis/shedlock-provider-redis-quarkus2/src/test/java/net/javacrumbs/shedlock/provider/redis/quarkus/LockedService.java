package net.javacrumbs.shedlock.provider.redis.quarkus;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.smallrye.common.annotation.Blocking;
import net.javacrumbs.shedlock.cdi.SchedulerLock;

@ApplicationScoped
public class LockedService{
    
    private static final Logger LOG = LoggerFactory.getLogger(LockedService.class);
    
    private AtomicInteger count = new AtomicInteger(0);
    
    @SchedulerLock(name = "test")
    public void test(int time) {
        
        execute(time);
        
        LOG.info("Executing [DONE]");
        
    }
    
    public void execute(int time) {
        count.incrementAndGet();
        LOG.info("Executing ....(c="+count.get()+")");
        
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
        LOG.info("getc=("+count.get()+")");
        return count.get();
    }

    public void countReset() {
        count.set(0);
    }

}
