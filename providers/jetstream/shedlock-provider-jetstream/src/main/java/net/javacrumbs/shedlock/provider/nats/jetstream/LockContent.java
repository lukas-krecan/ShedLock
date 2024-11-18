package net.javacrumbs.shedlock.provider.nats.jetstream;

import java.time.Instant;

import net.javacrumbs.shedlock.support.annotation.NonNull;

public record LockContent(@NonNull Instant lockAtLeastUntil, @NonNull Instant lockAtMostUntil) {

}
