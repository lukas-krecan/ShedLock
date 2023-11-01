package net.javacrumbs.shedlock.provider.redis.quarkus;

import java.util.concurrent.atomic.AtomicInteger;
import javax.enterprise.context.ApplicationScoped;
import net.javacrumbs.shedlock.cdi.SchedulerLock;
import net.javacrumbs.shedlock.core.LockAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LockedService {

    private static final Logger LOG = LoggerFactory.getLogger(LockedService.class);

    private final AtomicInteger count = new AtomicInteger(0);

    @SchedulerLock(name = "test")
    public void test() {
        LockAssert.assertLocked();

        execute();

        LOG.info("Executing [DONE]");
    }

    public void execute() {
        count.incrementAndGet();
        LOG.info("Executing ....(c=" + count.get() + ")");
    }

    public int count() {
        LOG.info("getc=(" + count.get() + ")");
        return count.get();
    }

    public void countReset() {
        count.set(0);
    }
}
