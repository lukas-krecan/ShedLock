package net.javacrumbs.shedlock.provider.commercetools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.custom_object.CustomObject;
import com.commercetools.api.models.custom_object.CustomObjectDraft;
import com.fasterxml.jackson.core.type.TypeReference;
import io.vrap.rmf.base.client.error.ConcurrentModificationException;
import io.vrap.rmf.base.client.error.NotFoundException;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CommercetoolsLockProviderTest {
    private ProjectApiRoot projectApiRoot;
    private CommercetoolsLockProvider lockProvider;

    @BeforeEach
    void setUp() {
        projectApiRoot = mock(ProjectApiRoot.class, RETURNS_DEEP_STUBS);
        lockProvider = new CommercetoolsLockProvider(projectApiRoot);
    }

    @Test
    void doesNotLockIfLockIsAlreadyObtained() {
        Instant now = Instant.now();
        setupLock(
                "myLockName",
                createCustomObject(
                        new LockValue(now.minusSeconds(1), Instant.now().plusSeconds(60), "host1"), 1L));

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig(
                "myLockName", Duration.ofMillis(200), Duration.ofMillis(200).dividedBy(2)));
        assertThat(lock).isEmpty();
    }

    @Test
    void lockIfLockIsNotFound() {
        when(projectApiRoot
                        .customObjects()
                        .withContainerAndKey("lock", "myLockName")
                        .get()
                        .executeBlocking()
                        .getBody())
                .thenThrow(NotFoundException.class);

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig(
                "myLockName", Duration.ofMillis(200), Duration.ofMillis(200).dividedBy(2)));
        assertThat(lock).isPresent();
    }

    @Test
    void doesNotLockIfConcurrentModification() {
        Instant now = Instant.now();
        setupLock(
                "myLockName",
                createCustomObject(
                        new LockValue(now.minusSeconds(1), Instant.now().plusSeconds(60), "host1"), 1L));
        when(projectApiRoot
                        .customObjects()
                        .post(Mockito.any(CustomObjectDraft.class))
                        .executeBlocking())
                .thenThrow(ConcurrentModificationException.class);

        Optional<SimpleLock> lock = lockProvider.lock(lockConfig(
                "myLockName", Duration.ofMillis(200), Duration.ofMillis(200).dividedBy(2)));
        assertThat(lock).isEmpty();
    }

    private void setupLock(String lockName, CustomObject customObject) {
        when(projectApiRoot
                        .customObjects()
                        .withContainerAndKey("lock", lockName)
                        .get()
                        .executeBlocking()
                        .getBody())
                .thenReturn(customObject);
    }

    private LockConfiguration lockConfig(String name, Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new LockConfiguration(ClockProvider.now(), name, lockAtMostFor, lockAtLeastFor);
    }

    private CustomObject createCustomObject(LockValue lockValue, Long version) {
        var customObject = mock(CustomObject.class);
        lenient()
                .when(customObject.getValue())
                .thenReturn(JsonUtils.createObjectMapper()
                        .convertValue(lockValue, new TypeReference<LinkedHashMap<String, String>>() {}));
        lenient().when(customObject.getVersion()).thenReturn(version);
        return customObject;
    }
}
