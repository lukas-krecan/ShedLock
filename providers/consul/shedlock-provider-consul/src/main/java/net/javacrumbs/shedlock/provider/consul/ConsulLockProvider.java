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

import java.time.Duration;
import java.util.Optional;

abstract class ConsulLockProvider implements LockProvider {

    final static Duration MIN_TTL = Duration.ofSeconds(10);
    final static Duration MAX_TTL = Duration.ofSeconds(86400);
    private String consulLockPostfix = "-leader";

    final ConsulClient consulClient;

    public ConsulLockProvider(ConsulClient consulClient) {
        this.consulClient = consulClient;
    }

    String createSession(String name, Duration lockTtl) {
        NewSession sessions = new NewSession();
        sessions.setName(name);
        sessions.setLockDelay(0);
        sessions.setBehavior(Session.Behavior.DELETE);
        long ttlInSeconds = Math.max(lockTtl.getSeconds(), MIN_TTL.getSeconds());
        sessions.setTtl(ttlInSeconds + "s");
        String sessionId = consulClient.sessionCreate(sessions, QueryParams.DEFAULT).getValue();
        getLogger().info("Acquired session {} for {} seconds", sessionId, ttlInSeconds);
        return sessionId;
    }

    Optional<SimpleLock> tryLock(String sessionId, LockConfiguration lockConfiguration) {
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

    public ConsulLockProvider setConsulLockPostfix(String consulLockPostfix) {
        this.consulLockPostfix = consulLockPostfix;
        return this;
    }

    abstract Logger getLogger();

    protected abstract void unlock(String sessionId, LockConfiguration lockConfiguration);
}
