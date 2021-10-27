/**
 * Copyright 2009 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.kubernetes;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static net.javacrumbs.shedlock.core.ClockProvider.now;

/**
 * Lock using ElasticSearch &gt;= 6.4.0.
 * Requires elasticsearch-rest-high-level-client &gt; 6.4.0
 * <p>
 * It uses a collection that contains documents like this:
 * <pre>
 * {
 *    "name" : "lock name",
 *    "lockUntil" :  {
 *      "type":   "date",
 *      "format": "epoch_millis"
 *    },
 *    "lockedAt" : {
 *      "type":   "date",
 *      "format": "epoch_millis"
 *    }:
 *    "lockedBy" : "hostname"
 * }
 * </pre>
 * <p>
 * lockedAt and lockedBy are just for troubleshooting and are not read by the code
 *
 * <ol>
 * <li>
 * Attempts to insert a new lock record. As an optimization, we keep in-memory track of created lock records. If the record
 * has been inserted, returns lock.
 * </li>
 * <li>
 * We will try to update lock record using filter _id == name AND lock_until &lt;= now
 * </li>
 * <li>
 * If the update succeeded (1 updated document), we have the lock. If the update failed (0 updated documents) somebody else holds the lock
 * </li>
 * <li>
 * When unlocking, lock_until is set to now.
 * </li>
 * </ol>
 */
public class KubernetesLockProvider implements LockProvider {
    static final String CONFIGMAP_PREFIX = "shedlock";
    static final String LOCK_UNTIL = "lockUntil";
    static final String LOCK_AT_LEAST_FOR = "lockAtLeastFor";
    static final String LOCKED_AT = "lockedAt";
    static final String LOCKED_BY = "lockedBy";
    static final String NAME = "name";
    static final String KUBERNETES_LABEL = "shedlock.provider.kubernetes";

    private final NamespacedKubernetesClient client;

    public KubernetesLockProvider(@NonNull NamespacedKubernetesClient client) {
        this.client = client;
    }

    public static String getConfigmapName(String name) {
        String configMapName = String.format("%s-%s", CONFIGMAP_PREFIX, name).toLowerCase();
        if (configMapName.length() > 63) {
            return configMapName.substring(0, 63);
        }
        return configMapName;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        // There are three possible situations:
        // 1. The lock configMap does not exist yet - it is inserted - we have the lock
        // 2. The lock configMap exists and lockUntil <= now - it is updated - we have the lock
        // 3. The lock configMap exists and lockUntil > now - Duplicate key exception is thrown

        ConfigMap existingConfigMap = client.configMaps()
            .withName(getConfigmapName(lockConfiguration.getName()))
            .get();

        // 3. case
        if (existingConfigMap != null) {
            Map<String, String> lockData = existingConfigMap.getData();
            if (lockData == null) {
                throw new LockException("Lock information is not readable from configMap");
            }

            Instant lockUntil = Instant.ofEpochMilli(Long.parseLong(lockData.get(LOCK_UNTIL)));
            Duration lockAtLeastFor = Duration.ofMillis(Long.parseLong(lockData.get(LOCK_AT_LEAST_FOR)));
            Instant lockedAt = Instant.ofEpochMilli(Long.parseLong(lockData.get(LOCKED_AT)));
            if (!now().isAfter(lockUntil) || !now().isAfter(lockedAt.plus(lockAtLeastFor))) {
                return Optional.empty();
            }
        }

        // 1. & 2. case
        ConfigMap configMap = lockObject(lockConfiguration.getName(),
            lockConfiguration.getLockAtMostUntil(),
            lockConfiguration.getLockAtLeastFor(),
            now());
        client.configMaps().createOrReplace(configMap);
        return Optional.of(new KubernetesSimpleLock(lockConfiguration));
    }

    private ConfigMap lockObject(String name, Instant lockUntil, Duration lockAtLeastFor, Instant lockedAt) {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.trim().length() == 0) {
            hostname = KUBERNETES_LABEL;
        }
        return new ConfigMapBuilder()
            .editOrNewMetadata()
            .withName(getConfigmapName(name))
            .addToLabels(KUBERNETES_LABEL, name)
            .endMetadata()
            .addToData(NAME, name)
            .addToData(LOCKED_BY, hostname)
            .addToData(LOCKED_AT, String.valueOf(lockedAt.toEpochMilli()))
            .addToData(LOCK_UNTIL, String.valueOf(lockUntil.toEpochMilli()))
            .addToData(LOCK_AT_LEAST_FOR, String.valueOf(lockAtLeastFor.toMillis()))
            .build();
    }

    private final class KubernetesSimpleLock extends AbstractSimpleLock {

        private KubernetesSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            ConfigMap configMap = client.configMaps()
                .withName(getConfigmapName(lockConfiguration.getName()))
                .get();
            configMap.getData()
                .put(LOCK_UNTIL, String.valueOf(now().toEpochMilli()));
            client.configMaps().replace(configMap);
        }
    }
}
