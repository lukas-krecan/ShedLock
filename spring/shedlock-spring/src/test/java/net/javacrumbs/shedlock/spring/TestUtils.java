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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.argThat;

public class TestUtils {

    private static final int GAP = 100;

    private static final Logger logger = LoggerFactory.getLogger(TestUtils.class);

    public static LockConfiguration hasParams(String name, long lockAtMostFor) {
        return argThat(c ->
            name.equals(c.getName())
                && isNearTo(lockAtMostFor, c.getLockAtMostUntil())
        );
    }

    private static boolean isNearTo(long expected, Instant time) {
        Instant now = Instant.now();
        Instant from = now.plusMillis(expected - GAP);
        Instant to = now.plusMillis(expected);
        logger.info("Asserting time={} to be between {} and {}", time, from, to);
        return time.isAfter(from) && !time.isAfter(to);
    }
}
