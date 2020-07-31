package net.javacrumbs.shedlock.provider.consul;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.kv.model.GetValue;
import com.ecwid.consul.v1.kv.model.PutParams;
import com.ecwid.consul.v1.session.model.NewSession;
import com.ecwid.consul.v1.session.model.Session;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
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
 */
public class ConsulTtlLockProvider implements LockProvider {
    final static String CONSUL_LOCK_POSTFIX = "-leader";
    final static Duration MIN_TTL = Duration.ofSeconds(10);
    private final static Logger logger = LoggerFactory.getLogger(ConsulTtlLockProvider.class);
    private final ConsulClient consulClient;

    public ConsulTtlLockProvider(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        if (isLocked(lockConfiguration)) {
            return Optional.empty();
        }
        String sessionId = createSession(lockConfiguration.getName(), lockConfiguration.getLockAtMostFor());
        return tryLock(sessionId, lockConfiguration);
    }

    void unlock(String sessionId, LockConfiguration lockConfiguration) {
        Duration additionalSessionTtl = Duration.between(now(), lockConfiguration.getLockAtLeastUntil());
        consulClient.sessionDestroy(sessionId, QueryParams.DEFAULT);
        if (!additionalSessionTtl.isNegative() && additionalSessionTtl.getNano() > 0) {
            logger.info("Additional locking is required.");
            String newSessionId = createSession(lockConfiguration.getName(), additionalSessionTtl);
            tryLock(newSessionId, lockConfiguration);
        }
    }

    @Nonnull
    Optional<SimpleLock> extend(String sessionId, LockConfiguration lockConfiguration) {
        consulClient.renewSession(sessionId, QueryParams.DEFAULT);
        return tryLock(sessionId, lockConfiguration);
    }

    private String createSession(String name, Duration lockTtl) {
        NewSession sessions = new NewSession();
        sessions.setName(name);
        sessions.setLockDelay(0);
        sessions.setBehavior(Session.Behavior.DELETE);
        long ttlInSeconds = Math.max(lockTtl.getSeconds(), MIN_TTL.getSeconds());
        sessions.setTtl(ttlInSeconds + "s");
        String sessionId = consulClient.sessionCreate(sessions, QueryParams.DEFAULT).getValue();
        logger.error("Acquired session {} for {} seconds", sessionId, ttlInSeconds);
        return sessionId;
    }

    private Optional<SimpleLock> tryLock(String sessionId, LockConfiguration lockConfiguration) {
        PutParams putParams = new PutParams();
        putParams.setAcquireSession(sessionId);
        String jobName = getJobName(lockConfiguration);
        boolean isLockSuccessful = consulClient.setKVValue(jobName, lockConfiguration.getName(), putParams).getValue();
        if (isLockSuccessful) {
            return Optional.of(new ConsulSimpleLock(lockConfiguration, this, sessionId, now()));
        }
        return Optional.empty();
    }

    private String getJobName(LockConfiguration lockConfiguration) {
        return lockConfiguration.getName() + CONSUL_LOCK_POSTFIX;
    }

    private boolean isLocked(LockConfiguration lockConfiguration) {
        Response<GetValue> maybeLock = consulClient.getKVValue(getJobName(lockConfiguration));
        return null != maybeLock.getValue() && null != maybeLock.getValue().getSession();
    }
}
