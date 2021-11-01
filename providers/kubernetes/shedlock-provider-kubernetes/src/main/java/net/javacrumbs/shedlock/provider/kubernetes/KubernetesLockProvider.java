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

import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.ConfigMapLock;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KubernetesLockProvider implements LockProvider {
    static final String CONFIGMAP_PREFIX = "shedlock";

    private final NamespacedKubernetesClient client;
    private final String hostname;

    public KubernetesLockProvider(@NonNull NamespacedKubernetesClient client, @NonNull String hostname) {
        this.client = client;
        this.hostname = hostname;
    }

    public static String getConfigmapName(@NonNull LockConfiguration lockConfiguration) {
        String configMapName = String.format("%s-%s", CONFIGMAP_PREFIX, lockConfiguration.getName()).toLowerCase();
        if (configMapName.length() > 63) {
            return configMapName.substring(0, 63);
        }
        return configMapName;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        try {
            final CompletableFuture<Optional<SimpleLock>> completableFuture = new CompletableFuture<>();

            CompletableFuture.runAsync(() -> {
                client.leaderElector()
                    .withConfig(
                        new LeaderElectionConfigBuilder()
                            .withName(lockConfiguration.getName())
                            .withLeaseDuration(Duration.of(15, ChronoUnit.SECONDS))
                            .withRenewDeadline(Duration.of(10, ChronoUnit.SECONDS))
                            .withRetryPeriod(Duration.of(2, ChronoUnit.SECONDS))
                            .withLock(
                                new ConfigMapLock(
                                    client.getNamespace(),
                                    getConfigmapName(lockConfiguration),
                                    hostname)
                            )
                            .withLeaderCallbacks(new LeaderCallbacks(() -> {
                                // This process has just become the new leader and now holds the lock.
                                completableFuture.complete(Optional.of(new KubernetesSimpleLock(lockConfiguration)));
                            }, () -> {
                                // This process has stopped being leader and does not hold the lock.
                                completableFuture.complete(Optional.empty());
                            }, newLeaderId -> {
                                // This process is the new leader and now holds the lock.
                                completableFuture.complete(Optional.of(new KubernetesSimpleLock(lockConfiguration)));
                            })).build()
                    )
                    .build()
                    .run();
            });

            return completableFuture.get(1L, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // No leader events happened, so the lock is held by another process.
            return Optional.empty();
        } catch (ExecutionException | InterruptedException e) {
            throw new LockException("Unexpected error occurred", e);
        }
    }

    private final class KubernetesSimpleLock extends AbstractSimpleLock {

        private KubernetesSimpleLock(LockConfiguration lockConfiguration) {
            super(lockConfiguration);
        }

        @Override
        public void doUnlock() {
            // Remove information about current leader. Force clients to acquire new lock.
            client.configMaps().withName(getConfigmapName(lockConfiguration)).delete();
        }
    }
}
