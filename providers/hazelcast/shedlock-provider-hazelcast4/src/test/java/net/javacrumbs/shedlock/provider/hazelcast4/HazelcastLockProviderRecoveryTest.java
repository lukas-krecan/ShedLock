package net.javacrumbs.shedlock.provider.hazelcast4;

import com.hazelcast.client.HazelcastClientNotActiveException;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

import static java.time.temporal.ChronoUnit.MINUTES;
import static net.javacrumbs.shedlock.provider.hazelcast4.HazelcastLockProvider.LOCK_STORE_KEY_DEFAULT;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class HazelcastLockProviderRecoveryTest {
    @Mock
    private Supplier<HazelcastInstance> instanceSupplier;

    @Mock
    private IMap<Object, Object> map;

    @Mock
    private HazelcastInstance instance;

    @Test
    void shouldRecoverFromBrokenClientConnection() {
        when(instanceSupplier.get()).thenReturn(instance);
        when(instance.getMap(LOCK_STORE_KEY_DEFAULT))
            .thenThrow(new HazelcastClientNotActiveException())
            .thenReturn(map);
        HazelcastLockProvider lockProvider = new HazelcastLockProvider(instanceSupplier);

        Optional<SimpleLock> result = lockProvider.lock(lockConfig());

        assertThat(result.isPresent()).isTrue();
    }

    protected static LockConfiguration lockConfig() {
        return new LockConfiguration(ClockProvider.now(), HazelcastLockProvider.LOCK_STORE_KEY_DEFAULT, Duration.of(5, MINUTES), Duration.ZERO);
    }
}
