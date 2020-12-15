package net.javacrumbs.shedlock.spring;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.lang.reflect.Method;
import java.util.Optional;

public interface ExtendedLockConfigurationExtractor extends LockConfigurationExtractor {
    /**
     * Extracts lock configuration for given method
     */
    @NonNull
    Optional<LockConfiguration> getLockConfiguration(Object object, Method method);
}
