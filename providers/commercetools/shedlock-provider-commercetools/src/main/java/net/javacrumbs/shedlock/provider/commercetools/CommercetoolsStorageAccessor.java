package net.javacrumbs.shedlock.provider.commercetools;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.models.custom_object.CustomObject;
import com.commercetools.api.models.custom_object.CustomObjectDraftBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vrap.rmf.base.client.ApiHttpException;
import io.vrap.rmf.base.client.ApiHttpResponse;
import io.vrap.rmf.base.client.error.NotFoundException;
import io.vrap.rmf.base.client.http.HttpStatusCode;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import java.time.Instant;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.support.AbstractStorageAccessor;

class CommercetoolsStorageAccessor extends AbstractStorageAccessor {
    static final String LOCK_CONTAINER = "lock";
    private final ProjectApiRoot projectApiRoot;
    private final ObjectMapper objectMapper;

    CommercetoolsStorageAccessor(ProjectApiRoot projectApiRoot) {
        this.projectApiRoot = projectApiRoot;
        this.objectMapper = JsonUtils.createObjectMapper();
    }

    @Override
    public boolean insertRecord(LockConfiguration lockConfiguration) {
        if (find(lockConfiguration.getName()) != null) {
            return false;
        }
        try {
            return createOrUpdateLockValue(lockConfiguration.getName(), null, lockConfiguration.getLockAtMostUntil());
        } catch (ApiHttpException ex) {
            logger.warn("Error on insert", ex);
            return false;
        }
    }

    private boolean createOrUpdateLockValue(String lockName, Long version, Instant until) {
        LockValue lockValue = new LockValue(ClockProvider.now(), until, getHostname());
        CustomObjectDraftBuilder customObjectDraftBuilder = CustomObjectDraftBuilder.of()
                .container(LOCK_CONTAINER)
                .key(lockName)
                .value(lockValue);
        if (version != null) {
            customObjectDraftBuilder.version(version);
        }
        ApiHttpResponse<CustomObject> response = projectApiRoot
                .customObjects()
                .post(customObjectDraftBuilder.build())
                .executeBlocking();
        return response.getStatusCode() < 400;
    }

    private VersionedLockValue find(String lockName) {
        try {
            ApiHttpResponse<CustomObject> response = projectApiRoot
                    .customObjects()
                    .withContainerAndKey(LOCK_CONTAINER, lockName)
                    .get()
                    .executeBlocking();
            if (response.getStatusCode() == HttpStatusCode.NOT_FOUND_404) {
                return null;
            }
            CustomObject customObject = response.getBody();
            LockValue lockValue = objectMapper.convertValue(customObject.getValue(), LockValue.class);
            return new VersionedLockValue(customObject.getVersion(), lockValue);
        } catch (NotFoundException ex) {
            return null;
        }
    }

    @Override
    public boolean updateRecord(LockConfiguration lockConfiguration) {
        VersionedLockValue lockValue = find(lockConfiguration.getName());
        if (lockValue == null || lockValue.lockValue().lockUntil().isAfter(ClockProvider.now())) {
            return false;
        }

        try {
            return createOrUpdateLockValue(
                    lockConfiguration.getName(), lockValue.version(), lockConfiguration.getLockAtMostUntil());
        } catch (ApiHttpException e) {
            logger.warn("Error on update", e);
            return false;
        }
    }

    @Override
    public void unlock(LockConfiguration lockConfiguration) {
        updateUntil(lockConfiguration.getName(), lockConfiguration.getUnlockTime());
    }

    private boolean updateUntil(String name, Instant until) {
        VersionedLockValue versionedLockValue = find(name);
        if (versionedLockValue != null
                && versionedLockValue.lockValue().lockedBy().equals(getHostname())
                && versionedLockValue.lockValue().lockUntil().isAfter(ClockProvider.now())) {
            try {
                createOrUpdateLockValue(name, versionedLockValue.version(), until);
                return true;
            } catch (ApiHttpException ex) {
                logger.warn("Error on updateUntil", ex);
                return false;
            }
        }
        return false;
    }

    @Override
    public boolean extend(LockConfiguration lockConfiguration) {
        VersionedLockValue lockValue = find(lockConfiguration.getName());
        if (lockValue == null
                || lockValue.lockValue().lockUntil().isBefore(ClockProvider.now())
                || !lockValue.lockValue().lockedBy().equals(getHostname())) {
            logger.trace("extend false");
            return false;
        }

        return updateUntil(lockConfiguration.getName(), lockConfiguration.getLockAtMostUntil());
    }
}
