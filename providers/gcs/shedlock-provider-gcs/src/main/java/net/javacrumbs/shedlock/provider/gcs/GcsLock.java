package net.javacrumbs.shedlock.provider.gcs;

import java.time.Instant;

record GcsLock(Instant lockUntil, Instant lockedAt, String lockedBy, long generation) {}
