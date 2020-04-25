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
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.execute.Execution;
import org.apache.geode.cache.execute.FunctionService;
import org.apache.geode.cache.execute.ResultCollector;
import org.apache.geode.internal.cache.execute.DefaultResultCollector;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * GeodeLockProvider.
 * <p>
 * Implementation of {@link LockProvider} using Apache Geode for store and share locks informations and mechanisms between a cluster members
 * <p>
 * Below, the mechanims :
 * - The Lock, an instance of {@link GeodeLock}, is obtained / created when :
 * -- the lock is not not already locked by other process (lock - referenced by its name - is not present in the Distributed Locking Service)
 * - Unlock action :
 * -- removes lock object when {@link LockConfiguration#lockAtLeastFor} min lease time is expired
 * -- removes lock object instantly if {@link LockConfiguration#lockAtLeastFor} is not defined
 */
public class GeodeLockProvider implements LockProvider {

    private static final Logger log = LoggerFactory.getLogger(GeodeLockProvider.class);

    private final ClientCache clientCache;

    private DistributedLockFunction distributedLockFunction = new DistributedLockFunction();

    /**
     * GeodeLockProvider Constructor
     * @param clientCache ${@link ClientCache}
     */
    public GeodeLockProvider(ClientCache clientCache){
        this.clientCache = clientCache;
    }

    /**
     * Attempts to obtained a {@link SimpleLock} by executing a Geode Function on the cluster,
     * the function only executes once and is fault tolerant to failures.
     * @param lockConfiguration: {@link LockConfiguration}
     * @return {@link Optional of {@link SimpleLock}}
     */
    @Override
    public @NotNull Optional<SimpleLock> lock(@NotNull LockConfiguration lockConfiguration) {
        log.trace("lock - Attempt : {}", lockConfiguration);
        boolean isLocked = executeFunction(lockConfiguration,Constants.LOCK);
        if(isLocked){
            return Optional.ofNullable(new GeodeLock(this,lockConfiguration));
        }
        return Optional.empty();
    }

    private boolean executeFunction(@NotNull LockConfiguration lockConfiguration,String operation) {
        Execution execution = FunctionService.onServer(this.clientCache.getDefaultPool())
            .setArguments(new Object[]{ lockConfiguration.getName(),operation,keyLockTime(lockConfiguration) })
            .withCollector(new DefaultResultCollector());
        ResultCollector rc = execution.execute(distributedLockFunction.getId());
        List arrayList = (ArrayList)rc.getResult();
        Object o = arrayList.get(0);
        if(o instanceof Boolean){
            return (boolean) arrayList.get(0);
        } else if(o instanceof Exception){
            log.error("lock - Distributed Lock Function Exception {}",o);
        }
        return false;
    }


    private long keyLockTime(LockConfiguration lockConfiguration) {
        Duration between = Duration.between(ClockProvider.now(), lockConfiguration.getLockAtMostUntil());
        return between.toMillis();
    }

    /**
     * Unlocks a {@link GeodeLock} if lease least time is expired ,
     * else wait for the lease time to expire.
     * @param lockConfiguration : {@link LockConfiguration}
     */
    public void unlock(LockConfiguration lockConfiguration) {
        String lockName = lockConfiguration.getName();
        log.info("unlock - attempt : {}", lockName);
        final Instant now = ClockProvider.now();
        final Instant lockAtLeastInstant = lockConfiguration.getLockAtLeastUntil();
        long millisLeft = Duration.between(now, lockAtLeastInstant).toMillis();
        if (millisLeft <=0) {
            boolean isUnlocked = executeFunction(lockConfiguration,Constants.UNLOCK);
            log.info("unlock - done : {}", isUnlocked);
        } else {
            log.info("unlock - will be done after ms: {}", Duration.between(now,lockAtLeastInstant).toMillis());
            CompletableFuture.runAsync(()->{
                try {
                    Thread.sleep(Duration.between(now,lockAtLeastInstant).toMillis());
                } catch (InterruptedException e) { }
                boolean isUnlocked = executeFunction(lockConfiguration,Constants.UNLOCK);
                log.debug("unlock - done : {}", isUnlocked);
            });
        }
    }

}
