package net.javacrumbs.shedlock.test.boot;

import net.javacrumbs.shedlock.core.LockAssert;
import org.junit.jupiter.api.Test;

public class LockUnitTest {
    @Test
    public void shouldWork() {
        LockAssert.TestHelper.makeAllAssertsPass(true);
        new ScheduledTasks().reportCurrentTime();
    }
}
