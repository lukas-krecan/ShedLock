package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.kv.model.PutParams;
import com.ecwid.consul.v1.session.model.NewSession;
import com.ecwid.consul.v1.session.model.Session;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
public class ConsulLockProvider implements LockProvider {
    private final static Logger logger = LoggerFactory.getLogger(ConsulLockProvider.class);
    private volatile ScheduledExecutorService unlockScheduler;
    private Duration minSessionTtl = Duration.ofSeconds(10);

    private String consulLockPostfix = "-leader";
    private final ConsulClient consulClient;


    public ConsulLockProvider(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        String sessionId = createSession(lockConfiguration);
        return tryLock(sessionId, lockConfiguration);
    }

    void unlock(String sessionId, LockConfiguration lockConfiguration) {
        Duration additionalSessionTtl = Duration.between(now(), lockConfiguration.getLockAtLeastUntil());
        if (!additionalSessionTtl.isNegative() && !additionalSessionTtl.isZero()) {
            logger.info("Lock will still be held for {}", additionalSessionTtl);
            scheduleUnlock(sessionId, additionalSessionTtl);
        } else {
            destroy(sessionId);
        }
    }

    private String createSession(LockConfiguration lockConfiguration) {
        long ttlInSeconds = Math.max(lockConfiguration.getLockAtMostFor().getSeconds(), minSessionTtl.getSeconds());
        NewSession newSession = new NewSession();
        newSession.setName(lockConfiguration.getName());
        newSession.setLockDelay(0);
        newSession.setBehavior(Session.Behavior.DELETE);
        newSession.setTtl(ttlInSeconds + "s");
        String sessionId = consulClient.sessionCreate(newSession, QueryParams.DEFAULT).getValue();
        logger.info("Acquired session {} for {} seconds", sessionId, ttlInSeconds);
        return sessionId;
    }

    private Optional<SimpleLock> tryLock(String sessionId, LockConfiguration lockConfiguration) {
        PutParams putParams = new PutParams();
        putParams.setAcquireSession(sessionId);
        String leaderKey = getLeaderKey(lockConfiguration);
        boolean isLockSuccessful = consulClient.setKVValue(leaderKey, lockConfiguration.getName(), putParams).getValue();
        if (isLockSuccessful) {
            return Optional.of(new ConsulSimpleLock(lockConfiguration, this, sessionId));
        }
        return Optional.empty();
    }

    private String getLeaderKey(LockConfiguration lockConfiguration) {
        return lockConfiguration.getName() + consulLockPostfix;
    }

    private void scheduleUnlock(String sessionId, Duration unlockTime) {
        if (null == unlockScheduler) {
            synchronized (this) {
                if (null == unlockScheduler) {
                    unlockScheduler = Executors.newSingleThreadScheduledExecutor();
                }
            }
        }
        unlockScheduler.schedule(
            catchExceptions(() -> destroy(sessionId)),
            unlockTime.toMillis(), TimeUnit.MILLISECONDS
        );
    }

    private void destroy(String sessionId) {
        logger.info("Destroying session {}", sessionId);
        consulClient.sessionDestroy(sessionId, QueryParams.DEFAULT);
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

    public ConsulLockProvider setConsulLockPostfix(String consulLockPostfix) {
        this.consulLockPostfix = consulLockPostfix;
        return this;
    }

    public ConsulLockProvider setMinSessionTtl(Duration minSessionTtl) {
        this.minSessionTtl = minSessionTtl;
        return this;
    }
}
