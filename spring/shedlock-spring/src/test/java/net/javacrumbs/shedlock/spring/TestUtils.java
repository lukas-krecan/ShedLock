package net.javacrumbs.shedlock.spring; /**
 * Copyright 2009-2017 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.javacrumbs.shedlock.core.LockConfiguration;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;

public class TestUtils {

    private static final int GAP = 50;

    public static LockConfiguration hasParams(String name, int lockAtMostFor, int lockAtLeastFor) {
        return argThat(c ->
            name.equals(c.getName())
                && isNearTo(lockAtMostFor, c.getLockAtMostUntil())
                && isNearTo(lockAtLeastFor, c.getLockAtLeastUntil())
        );
    }

    private static boolean isNearTo(int lockAtMostFor, Instant time) {
        Instant now = Instant.now();
        return !time.isAfter(now.plusMillis(lockAtMostFor)) && time.isAfter(now.plusMillis(lockAtMostFor - GAP));
    }
}
