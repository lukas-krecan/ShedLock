package net.javacrumbs.shedlock.provider.nats.jetstream;

import java.time.Instant;

record LockContent(Instant lockAtLeastUntil, Instant lockAtMostUntil) {}
