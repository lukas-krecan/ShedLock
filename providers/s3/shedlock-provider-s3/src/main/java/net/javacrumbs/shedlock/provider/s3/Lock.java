package net.javacrumbs.shedlock.provider.s3;

import java.time.Instant;

record Lock(Instant lockUntil, Instant lockedAt, String lockedBy, String eTag) {}
