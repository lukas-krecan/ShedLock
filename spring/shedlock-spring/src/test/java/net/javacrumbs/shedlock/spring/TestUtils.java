/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockConfiguration;
import org.mockito.ArgumentMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.mockito.ArgumentMatchers.argThat;

public class TestUtils {

    private static final int GAP = 1000;

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    public static LockConfiguration hasParams(String name, long lockAtMostFor, long lockAtLeastFor) {
        return argThat(new ArgumentMatcher<LockConfiguration>() {
            @Override
            public boolean matches(LockConfiguration c) {
                return name.equals(c.getName())
                    && isNearTo(lockAtMostFor, c.getLockAtMostUntil())
                    && isNearTo(lockAtLeastFor, c.getLockAtLeastUntil());
            }

            @Override
            public String toString() {
                Instant now = now();
                return "hasParams(\"" + name + "\", " + now.plusMillis(lockAtMostFor) + ", " + now.plusMillis(lockAtLeastFor) + ")";
            }
        });
    }

    private static boolean isNearTo(long expected, Instant time) {
        Instant now = now();
        Instant from = now.plusMillis(expected - GAP);
        Instant to = now.plusMillis(expected);
        boolean isNear = time.isAfter(from) && !time.isAfter(to);
        if (!isNear) {
            logger.info("Assertion failed time={} is not between {} and {}", time, from, to);
        }
        return isNear;
    }
}
