package net.javacrumbs.micronaut.test;

import io.micronaut.test.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@MicronautTest
public class ApplicationTest {
    @Inject
    private ScheduledTasks scheduledTasks;

    @Test
    void shouldStart() {
        await().untilAsserted(() -> {
            assertThat(scheduledTasks.wasCalled()).isTrue();
        });
    }
}
