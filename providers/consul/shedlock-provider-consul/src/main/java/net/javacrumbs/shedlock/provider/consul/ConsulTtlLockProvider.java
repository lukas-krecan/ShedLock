package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;

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
 * <p>The lock is acquired for the time specified in {@code @SchedulerLock.lockAtMostFor}. Please note that this lock provider
 * doesn't make any correction to the aforementioned TTL behaviour so most likely your locked session will live for
 * longer than specified in lockAtMostFor. In this lock provider there is no session renewal done in the background.</p>
 *
 * <p>If {@code @SchedulerLock.lockAtLeastFor} is specified, you should be aware about one caveat consul has.
 * <a href="https://www.consul.io/api-docs/session">The minimum session TTL is 10 seconds</a>. When session is unlocked within lockAtLeastFor
 * period, new session will be created for a TTL minimum 10 seconds minimum. Keeping in mind the aforementioned double TTL
 * even if you have 1 second left to hold for lockAtLeastFor, most likely the lock time be 10 seconds minimum and most
 * likely it will be 20 seconds.</p>
 *
 * <p>If you need more granularity and if your lockAtMostFor is small, it's advised to use {@link ConsulSchedulableLockProvider}</p>
 *
 * @author Artur Kalimullin
 */
public class ConsulTtlLockProvider extends ConsulLockProvider {
    private final static Logger logger = LoggerFactory.getLogger(ConsulTtlLockProvider.class);

    public ConsulTtlLockProvider(ConsulClient consulClient) {
        super(consulClient);
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        if (isLocked(lockConfiguration)) {
            return Optional.empty();
        }
        String sessionId = createSession(lockConfiguration.getName(), lockConfiguration.getLockAtMostFor());
        return tryLock(sessionId, lockConfiguration);
    }

    @Override
    protected void unlock(String sessionId, LockConfiguration lockConfiguration) {
        Duration additionalSessionTtl = Duration.between(now(), lockConfiguration.getLockAtLeastUntil());
        consulClient.sessionDestroy(sessionId, QueryParams.DEFAULT);
        if (!additionalSessionTtl.isNegative() && additionalSessionTtl.getNano() > 0) {
            logger.info("Additional locking is required.");
            String newSessionId = createSession(lockConfiguration.getName(), additionalSessionTtl);
            tryLock(newSessionId, lockConfiguration);
        }
    }
}
