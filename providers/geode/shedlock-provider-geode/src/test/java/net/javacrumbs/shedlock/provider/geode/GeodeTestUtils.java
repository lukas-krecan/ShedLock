/**
 * Copyright 2009-2020 the original author or authors.
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

package net.javacrumbs.shedlock.provider.geode;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import org.apache.geode.test.dunit.rules.ClientVM;
import org.apache.geode.test.dunit.rules.ClusterStartupRule;
import org.apache.geode.test.dunit.rules.MemberVM;

import java.time.Duration;
import java.time.Instant;

import static java.time.temporal.ChronoUnit.SECONDS;
import static org.apache.geode.distributed.ConfigurationProperties.SERIALIZABLE_OBJECT_FILTER;

public class GeodeTestUtils {

    public static int getLocatorPort(){
        return ClusterStartupRule.getDUnitLocatorPort();
    }

    public static ClientVM startClient(ClusterStartupRule clusterStartupRule,final int vmIndex) throws Exception {
        return clusterStartupRule.startClientVM(vmIndex,clientCacheFactory -> {
            clientCacheFactory.addPoolLocator("localhost",getLocatorPort());
        });
    }

    public static MemberVM startServer(ClusterStartupRule clusterStartupRule,final int vmIndex) {
        return clusterStartupRule.startServerVM(
            vmIndex,serverStarterRule -> {
                serverStarterRule.withPort(40404 + vmIndex).withConnectionToLocator(getLocatorPort())
                    .withProperty(SERIALIZABLE_OBJECT_FILTER,
                        new DistributedLockFunction().getClass().getName());
                return serverStarterRule;
            });
    }

    public static LockConfiguration simpleLockConfig(final String name,int lockTimeSec) {
        return lockConfig(name, Duration.of(lockTimeSec, SECONDS), Duration.ZERO);
    }

    public static LockConfiguration lockConfig(final String name, final Duration lockAtMostFor, final Duration lockAtLeastFor) {
        Instant now = ClockProvider.now();
        return new LockConfiguration(name, now.plus(lockAtMostFor), now.plus(lockAtLeastFor));
    }
}
