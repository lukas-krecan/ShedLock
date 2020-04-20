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
                serverStarterRule.withPort(40404).withConnectionToLocator(getLocatorPort())
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
