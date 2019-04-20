package micronaut.test;

import io.micronaut.runtime.Micronaut;
import io.micronaut.scheduling.annotation.Scheduled;
import net.javacrumbs.shedlock.micronaut.SchedulerLock;

public class Application {

    public static void main(String[] args) {
        Micronaut.run(Application.class);
    }

    @Scheduled(fixedRate = "1s")
    @SchedulerLock
    public void task() {
        System.out.println("Task");
    }
}