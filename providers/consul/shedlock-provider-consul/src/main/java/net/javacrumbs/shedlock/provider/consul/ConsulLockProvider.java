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
package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.kv.model.PutParams;
import com.ecwid.consul.v1.session.model.NewSession;
import com.ecwid.consul.v1.session.model.Session;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;

/**
 * <p>This lock provider registers a new session for lock and on unlock this session is removed together with all associated locks.</p>
 *
 * <p>The main point you need to be aware about is that consul holds session for up to twice TTL. That means,
 * even if the session TTL is set to 10 seconds, consul will hold still this session for 20 seconds.
 * This is an <a href="https://github.com/hashicorp/consul/issues/1172">expected behaviour</a> and it's impossible to change it.
 * This is the reason consul <a href="https://www.consul.io/docs/internals/sessions">recommends</a> to set the lowest possible TTL
 * and constantly extend it. With this lock it means that even if your lockAtMostFor is less that 20 seconds, the timeout will be
 * higher than 10 seconds and most likely will be 20.</p>
 *
 * <p>The lock is acquired for the time specified in {@code @SchedulerLock.lockAtMostFor}. Please note that this lock provider
 * doesn't make any correction to the aforementioned TTL behaviour so most likely your locked session will live for
 * longer than specified in lockAtMostFor. In this lock provider there is no session renewal done in the background.</p>
 *
 * @author Artur Kalimullin
 */
public class ConsulLockProvider implements LockProvider, AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ConsulLockProvider.class);
    private static final String DEFAULT_CONSUL_LOCK_POSTFIX = "-leader";
    private static final Duration DEFAULT_GRACEFUL_SHUTDOWN_INTERVAL = Duration.ofSeconds(2);
    private static final Duration DEFAULT_MIN_SESSION_TTL = Duration.ofSeconds(10);

    private final ScheduledExecutorService unlockScheduler = Executors.newSingleThreadScheduledExecutor();

    private final Configuration configuration;

    public ConsulLockProvider(@NonNull ConsulClient consulClient) {
        this(Configuration.builder()
            .withConsulClient(consulClient)
            .build()
        );
    }

    public ConsulLockProvider(@NonNull ConsulClient consulClient, Duration minSessionTtl) {
        this(Configuration.builder()
            .withConsulClient(consulClient)
            .withMinSessionTtl(minSessionTtl)
            .build()
        );
    }

    public ConsulLockProvider(
        @NonNull ConsulClient consulClient,
        Duration minSessionTtl,
        String consulLockPostfix,
        Duration gracefulShutdownInterval) {
        this(Configuration.builder()
            .withConsulClient(consulClient)
            .withMinSessionTtl(minSessionTtl)
            .withConsulLockPostfix(consulLockPostfix)
            .withGracefulShutdownInterval(gracefulShutdownInterval)
            .build()
        );
    }

    public ConsulLockProvider(@NonNull Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String sessionId = createSession(lockConfiguration);
        return tryLock(sessionId, lockConfiguration);
    }

    void unlock(String sessionId, LockConfiguration lockConfiguration) {
        Duration additionalSessionTtl = Duration.between(now(), lockConfiguration.getLockAtLeastUntil());
        if (!additionalSessionTtl.isNegative() && !additionalSessionTtl.isZero()) {
            logger.debug("Lock will still be held for {}", additionalSessionTtl);
            scheduleUnlock(sessionId, additionalSessionTtl);
        } else {
            destroy(sessionId);
        }
    }

    private String createSession(LockConfiguration lockConfiguration) {
        long ttlInSeconds = Math.max(lockConfiguration.getLockAtMostFor().getSeconds(),
            configuration.getMinSessionTtl().getSeconds());
        NewSession newSession = new NewSession();
        newSession.setName(lockConfiguration.getName());
        newSession.setLockDelay(0);
        newSession.setBehavior(Session.Behavior.DELETE);
        newSession.setTtl(ttlInSeconds + "s");

        String sessionId = client().sessionCreate(newSession, QueryParams.DEFAULT, token()).getValue();

        logger.debug("Acquired session {} for {} seconds", sessionId, ttlInSeconds);
        return sessionId;
    }

    private Optional<SimpleLock> tryLock(String sessionId, LockConfiguration lockConfiguration) {
        PutParams putParams = new PutParams();
        putParams.setAcquireSession(sessionId);
        String leaderKey = getLeaderKey(lockConfiguration);
        boolean isLockSuccessful = client().setKVValue(leaderKey, lockConfiguration.getName(), token(), putParams).getValue();

        if (isLockSuccessful) {
            return Optional.of(new ConsulSimpleLock(lockConfiguration, this, sessionId));
        }
        destroy(sessionId);
        return Optional.empty();
    }

    private String token() {
        return configuration.getToken();
    }

    private String getLeaderKey(LockConfiguration lockConfiguration) {

        if (configuration.getConsulLockPrefix() == null)
            return lockConfiguration.getName() + configuration.getConsulLockPostfix();

        return configuration.getConsulLockPrefix() + "/" + lockConfiguration.getName() + configuration.getConsulLockPostfix();
    }

    private void scheduleUnlock(String sessionId, Duration unlockTime) {
        unlockScheduler.schedule(
            catchExceptions(() -> destroy(sessionId)),
            unlockTime.toMillis(), TimeUnit.MILLISECONDS
        );
    }

    private void destroy(String sessionId) {
        logger.debug("Destroying session {}", sessionId);
        client().sessionDestroy(sessionId, QueryParams.DEFAULT, token());
    }

    private Runnable catchExceptions(Runnable runnable) {
        return () -> {
            try {
                runnable.run();
            } catch (Throwable t) {
                logger.warn("Exception while execution", t);
            }
        };
    }

    @Override
    public void close() {
        unlockScheduler.shutdown();
        try {
            if (!unlockScheduler.awaitTermination(configuration.getGracefulShutdownInterval().toMillis(),
                TimeUnit.MILLISECONDS)) {
                unlockScheduler.shutdownNow();
            }
        } catch (InterruptedException ignored) {
        }
    }

    private ConsulClient client() {
        return configuration.getConsulClient();
    }

    public static final class Configuration {
        private final Duration minSessionTtl;
        private final String consulLockPrefix;
        private final String consulLockPostfix;
        private final ConsulClient consulClient;
        private final Duration gracefulShutdownInterval;
        private final String token;

        /**
         * Use Builder to create.
         */
        Configuration(
            Duration minSessionTtl,
            String consulLockPostfix,
            ConsulClient consulClient,
            Duration gracefulShutdownInterval,
            String token, String consulLockPrefix) {

            this.minSessionTtl = minSessionTtl;
            this.consulLockPrefix = consulLockPrefix;
            this.consulLockPostfix = consulLockPostfix;
            this.consulClient = requireNonNull(consulClient, "consulClient can not be null");
            this.gracefulShutdownInterval = gracefulShutdownInterval;
            this.token = token;
        }

        public Duration getMinSessionTtl() {
            return minSessionTtl;
        }

        public String getConsulLockPostfix() {
            return consulLockPostfix;
        }

        public ConsulClient getConsulClient() {
            return consulClient;
        }

        public Duration getGracefulShutdownInterval() {
            return gracefulShutdownInterval;
        }

        public String getToken() {
            return token;
        }

        public String getConsulLockPrefix() {
            return consulLockPrefix;
        }

        public static Configuration.Builder builder() {
            return new Configuration.Builder();
        }

        public static final class Builder {

            private Duration minSessionTtl = DEFAULT_MIN_SESSION_TTL;
            private String consulLockPostfix = DEFAULT_CONSUL_LOCK_POSTFIX;
            private ConsulClient consulClient;
            private Duration gracefulShutdownInterval = DEFAULT_GRACEFUL_SHUTDOWN_INTERVAL;
            private String token;
            private String consulLockPrefix;

            public Builder withMinSessionTtl(Duration minSessionTtl) {
                this.minSessionTtl = minSessionTtl;
                return this;
            }

            public Builder withConsulLockPostfix(String consulLockPostfix) {
                this.consulLockPostfix = consulLockPostfix;
                return this;
            }

            public Builder withConsulClient(ConsulClient consulClient) {
                this.consulClient = consulClient;
                return this;
            }

            public Builder withGracefulShutdownInterval(Duration gracefulShutdownInterval) {
                this.gracefulShutdownInterval = gracefulShutdownInterval;
                return this;
            }

            public Builder withToken(String token) {
                this.token = token;
                return this;
            }

            public Builder withConsulLockPrefix(String leaderKeyPrefix) {
                this.consulLockPrefix = leaderKeyPrefix;
                return this;
            }

            public ConsulLockProvider.Configuration build() {
                return new ConsulLockProvider.Configuration(
                    minSessionTtl, consulLockPostfix, consulClient, gracefulShutdownInterval, token, consulLockPrefix);
            }
        }
    }

}
