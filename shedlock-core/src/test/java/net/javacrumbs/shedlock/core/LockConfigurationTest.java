package net.javacrumbs.shedlock.core;

import org.junit.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;


public class LockConfigurationTest {

    @Test
    public void lockAtLeastUnitilShouldBeBeforeOrEqualsToLockAtMostUntil() {
        Instant time = Instant.now().plusSeconds(5);
        new LockConfiguration("name", time, time);
        new LockConfiguration("name", time.plusMillis(1), time);

        assertThatThrownBy(() -> new LockConfiguration("name", time, time.plusMillis(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void lockAtMostUntilHasToBeInTheFuture() {
        Instant now = Instant.now();
        assertThatThrownBy(() -> new LockConfiguration("name", now.minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }


    @Test
    public void nameShouldNotBeEmpty() {
        assertThatThrownBy(() -> new LockConfiguration("", Instant.now().plusSeconds(5))).isInstanceOf(IllegalArgumentException.class);
    }

}