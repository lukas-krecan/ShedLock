package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

/**
 * <p>This lock provider registers a new session for lock and on unlock this session is removed together with all associated locks.</p>
 *
 * <p>The main point you need to be aware about is that consul holds session for up to twice TTL. That means,
 * even if the session TTL is set to 10 seconds, consul will hold still this session for 20 seconds.
 * This is an <a href="https://github.com/hashicorp/consul/issues/1172">expected behaviour</a> and it's impossible to change it.
 * This is the reason consul <a href="https://www.consul.io/docs/internals/sessions">recommends</a> to set the lowest possible TTL
 * and constantly extend it.</p>
 *
 * <p>The lock is acquired for a defined session TTL (10 seconds by default). It will be constantly renewed in background
 * until lockAtMostFor. As minimum session time is 10 seconds, in case if session won't be unlocked, it may happen that
 * lock will be hold for more than lockAtMostFor but no more than 20 seconds.</p>
 *
 * <p>If {@code @SchedulerLock.lockAtLeastFor} is specified, the background thread will remove the associated session.</p>
 *
 * <p>The main purpose of this lock provider is to support granularity if you have low lockAtMostFor time or you have
 * lockAtLeastFor specified. The backside of it is that it's used a ScheduledExecutorService to perform some background
 * operations. If you have long-running tasks and big lockAtMostFor, you can use {@link ConsulTtlLockProvider}.
 * </p>
 *
 * @author Artur Kalimullin
 */
public class ConsulSchedulableLockProvider extends ConsulLockProvider {
    private final static Logger logger = LoggerFactory.getLogger(ConsulSchedulableLockProvider.class);
    private final Map<String, ScheduledFuture<?>> jobToScheduledTasksMap = new HashMap<>();
    private final Object scheduleMonitor = new Object();
    private final ScheduledExecutorService scheduler;
    private Duration sessionTtl = Duration.ofSeconds(10);

    public ConsulSchedulableLockProvider(ConsulClient consulClient) {
        super(consulClient);
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
        executor.setRemoveOnCancelPolicy(true);
        scheduler = executor;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        if (isLocked(lockConfiguration)) {
            return Optional.empty();
        }
        String sessionId = createSession(lockConfiguration.getName(), sessionTtl);
        Optional<SimpleLock> lock = tryLock(sessionId, lockConfiguration);
        lock.ifPresent(l -> {
            synchronized (scheduleMonitor) {
                // there should never be no associated tasks for this lock but in case there are - we need to cancel it
                cancelScheduledTaskIfPresent(lockConfiguration);
                ScheduledFuture<?> task;
                if (lockConfiguration.getLockAtMostFor().compareTo(sessionTtl) > 0) {
                    task = scheduler.scheduleAtFixedRate(
                        () -> renewUntilAtMostFor(sessionId, lockConfiguration),
                        sessionTtl.toMillis(), sessionTtl.toMillis(), TimeUnit.MILLISECONDS
                    );
                } else {
                    task = scheduler.schedule(
                        () -> unlock(sessionId, lockConfiguration),
                        lockConfiguration.getLockAtMostFor().toMillis(), TimeUnit.MILLISECONDS
                    );
                }
                jobToScheduledTasksMap.put(lockConfiguration.getName(), task);
            }
        });
        return lock;
    }

    private void renewUntilAtMostFor(String sessionId, LockConfiguration lockConfiguration) {
        renew(sessionId);
        Duration timeLeft = Duration.between(now(), lockConfiguration.getLockAtMostUntil());
        if (timeLeft.compareTo(sessionTtl) < 0) {
            synchronized (scheduleMonitor) {
                jobToScheduledTasksMap.remove(lockConfiguration.getName()).cancel(false);
                jobToScheduledTasksMap.put(
                    lockConfiguration.getName(),
                    scheduler.schedule(() -> unlock(sessionId, lockConfiguration), timeLeft.toMillis(), TimeUnit.MILLISECONDS)
                );
            }
        }
    }

    @Override
    protected void unlock(String sessionId, LockConfiguration lockConfiguration) {
        Duration additionalSessionTtl = Duration.between(now(), lockConfiguration.getLockAtLeastUntil());
        if (!additionalSessionTtl.isNegative() && !additionalSessionTtl.isZero()) {
            logger.info("Additional locking is required for session {}", sessionId);
            renew(sessionId);
            synchronized (scheduleMonitor) {
                jobToScheduledTasksMap.remove(lockConfiguration.getName()).cancel(true);
                jobToScheduledTasksMap.put(
                    lockConfiguration.getName(),
                    scheduler.schedule(() -> destroy(sessionId), additionalSessionTtl.toMillis(), TimeUnit.MILLISECONDS)
                );
            }
        } else {
            destroy(sessionId);
            synchronized (scheduleMonitor) {
                cancelScheduledTaskIfPresent(lockConfiguration);
            }
        }
    }

    private void cancelScheduledTaskIfPresent(LockConfiguration lockConfiguration) {
        Optional.ofNullable(jobToScheduledTasksMap.remove(lockConfiguration.getName()))
            .ifPresent(f -> f.cancel(true));
    }

    private void renew(String sessionId) {
        logger.debug("Renewing session {}", sessionId);
        consulClient.renewSession(sessionId, QueryParams.DEFAULT);
    }

    private void destroy(String sessionId) {
        logger.info("Destroying session {}", sessionId);
        consulClient.sessionDestroy(sessionId, QueryParams.DEFAULT);
    }

    public ConsulSchedulableLockProvider setSessionTtl(Duration sessionTtl) {
        if (sessionTtl.compareTo(MIN_TTL) < 0 || sessionTtl.compareTo(MAX_TTL) > 0) {
            throw new IllegalArgumentException(String.format("Session TTL must be from %s to %s", MIN_TTL, MAX_TTL));
        }
        this.sessionTtl = sessionTtl;
        return this;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }
}
