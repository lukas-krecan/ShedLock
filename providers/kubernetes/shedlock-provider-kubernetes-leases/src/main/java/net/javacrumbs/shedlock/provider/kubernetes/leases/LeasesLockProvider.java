package net.javacrumbs.shedlock.provider.kubernetes.leases;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.core.ClockProvider.now;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.Lease;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseBuilder;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseList;
import io.fabric8.kubernetes.api.model.coordination.v1.LeaseSpec;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import java.net.HttpURLConnection;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ExtensibleLockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.Utils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lock provider based on Kubernetes {@code coordination.k8s.io/v1} Lease
 * resources. Every ShedLock lock is represented by one Lease whose name equals
 * the lock name, so the lock name has to be a valid DNS-1123 subdomain (lower
 * case alphanumerics, '-' and '.').
 *
 * <p>
 * Mutual exclusion relies on the optimistic concurrency of the Kubernetes API
 * ({@code resourceVersion}): creating an existing Lease or updating a stale one
 * fails with HTTP 409, which means somebody else got the lock first.
 *
 * <p>
 * The exact lock expiry is stored in the {@value #LOCKED_UNTIL_ANNOTATION}
 * annotation (Lease durations only have a second granularity). The standard
 * Lease fields are filled in as well so the state is readable with
 * {@code kubectl get lease}.
 *
 * <p>
 * The service account needs {@code get}, {@code create} and {@code update}
 * permissions on {@code leases.coordination.k8s.io} in the used namespace.
 */
public class LeasesLockProvider implements ExtensibleLockProvider {
    static final String LOCKED_UNTIL_ANNOTATION = "shedlock.net/locked-until";
    private static final Logger logger = LoggerFactory.getLogger(LeasesLockProvider.class);

    private final KubernetesClient client;
    private final String namespace;
    private final String holderIdentity;

    /**
     * Uses the namespace the client is configured with. When running in a pod
     * this is the namespace of the pod (read from the service account), outside
     * a cluster it is the namespace of the current kubeconfig context.
     *
     * @param client
     *            Kubernetes client, its lifecycle is managed by the caller
     * @throws IllegalStateException
     *             if the client has no namespace configured
     */
    public LeasesLockProvider(KubernetesClient client) {
        this(client, detectNamespace(client));
    }

    /**
     * @param client
     *            Kubernetes client, its lifecycle is managed by the caller
     * @param namespace
     *            namespace where the Lease resources are stored
     */
    public LeasesLockProvider(KubernetesClient client, String namespace) {
        this(client, namespace, Utils.getHostname());
    }

    /**
     * @param holderIdentity
     *            value stored in {@code spec.holderIdentity}, informational only
     */
    public LeasesLockProvider(KubernetesClient client, String namespace, String holderIdentity) {
        this.client = requireNonNull(client);
        this.namespace = requireNonNull(namespace);
        this.holderIdentity = requireNonNull(holderIdentity);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        String name = lockConfiguration.getName();
        Instant now = now();
        Lease acquired;
        try {
            Optional<Lease> existing = findLease(name);
            if (existing.isEmpty()) {
                acquired = leases().resource(newLease(lockConfiguration, now)).create();
            } else {
                if (lockedUntil(existing.get()).isAfter(now)) {
                    return Optional.empty();
                }
                acquired = leases().resource(update(existing.get(), lockConfiguration.getLockAtMostUntil(), now, true))
                        .update();
            }
        } catch (KubernetesClientException e) {
            if (isConflict(e)) {
                logger.debug("Lock {} was taken by someone else", name);
                return Optional.empty();
            }
            throw e;
        }
        logger.debug("Locked {}", lockConfiguration);
        return Optional.of(new LeaseLock(lockConfiguration, acquired));
    }

    boolean isLocked(String lockName) {
        return findLease(lockName)
                .filter(lease -> lockedUntil(lease).isAfter(now()))
                .isPresent();
    }

    /** Fabric8 returns {@code null} when the Lease does not exist. */
    private Optional<Lease> findLease(String name) {
        return Optional.ofNullable(leases().withName(name).get());
    }

    private NonNamespaceOperation<Lease, LeaseList, Resource<@Nullable Lease>> leases() {
        return client.leases().inNamespace(namespace);
    }

    private Lease newLease(LockConfiguration configuration, Instant now) {
        Map<String, String> annotations = Map.of(
                LOCKED_UNTIL_ANNOTATION, configuration.getLockAtMostUntil().toString());
        return new LeaseBuilder()
                .withMetadata(new ObjectMetaBuilder()
                        .withName(configuration.getName())
                        .withNamespace(namespace)
                        .withAnnotations(annotations)
                        .build())
                .withSpec(spec(configuration.getLockAtMostUntil(), now))
                .build();
    }

    /** Returns a copy of the lease with a new expiry; keeps resourceVersion. */
    private Lease update(Lease lease, Instant lockedUntil, Instant now, boolean newHolder) {
        Map<String, String> annotations = new HashMap<>();
        if (lease.getMetadata().getAnnotations() != null) {
            annotations.putAll(lease.getMetadata().getAnnotations());
        }
        annotations.put(LOCKED_UNTIL_ANNOTATION, lockedUntil.toString());

        LeaseSpec oldSpec = lease.getSpec();
        ZonedDateTime acquireTime = newHolder || oldSpec == null ? utc(now) : oldSpec.getAcquireTime();
        int transitions = oldSpec != null && oldSpec.getLeaseTransitions() != null ? oldSpec.getLeaseTransitions() : 0;

        LeaseSpec newSpec = spec(lockedUntil, now);
        newSpec.setAcquireTime(acquireTime);
        newSpec.setLeaseTransitions(newHolder ? transitions + 1 : transitions);

        return new LeaseBuilder(lease)
                .editMetadata()
                .withAnnotations(annotations)
                .endMetadata()
                .withSpec(newSpec)
                .build();
    }

    private LeaseSpec spec(Instant lockedUntil, Instant now) {
        ZonedDateTime utcNow = utc(now);
        LeaseSpec spec = new LeaseSpec();
        spec.setHolderIdentity(holderIdentity);
        spec.setAcquireTime(utcNow);
        spec.setRenewTime(utcNow);
        spec.setLeaseDurationSeconds(
                (int) Math.max(1, Duration.between(now, lockedUntil).toSeconds()));
        spec.setLeaseTransitions(0);
        return spec;
    }

    private static Instant lockedUntil(Lease lease) {
        Map<String, String> annotations = lease.getMetadata().getAnnotations();
        String value = annotations == null ? null : annotations.get(LOCKED_UNTIL_ANNOTATION);
        if (value == null) {
            // Not created by ShedLock, treat as not locked.
            return Instant.MIN;
        }
        return Instant.parse(value);
    }

    private static ZonedDateTime utc(Instant instant) {
        return ZonedDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String detectNamespace(KubernetesClient client) {
        String detected = requireNonNull(client).getNamespace();
        if (detected == null || detected.isBlank()) {
            throw new IllegalStateException("Unable to detect the Kubernetes namespace, please specify it explicitly");
        }
        return detected;
    }

    private static boolean isConflict(KubernetesClientException e) {
        return e.getCode() == HttpURLConnection.HTTP_CONFLICT;
    }

    private final class LeaseLock extends AbstractSimpleLock {
        private final String resourceVersion;

        private LeaseLock(LockConfiguration lockConfiguration, Lease lease) {
            super(lockConfiguration);
            this.resourceVersion = requireNonNull(lease.getMetadata().getResourceVersion());
        }

        private boolean ownsLease(Lease lease) {
            String currentVersion = lease.getMetadata().getResourceVersion();
            if (!resourceVersion.equals(currentVersion)) {
                logger.warn(
                        "Lease {} has changed since it was acquired (resource version: {}, current: {})",
                        lockConfiguration.getName(),
                        resourceVersion,
                        currentVersion);
                return false;
            }
            return true;
        }

        @Override
        protected void doUnlock() {
            String name = lockConfiguration.getName();
            Optional<Lease> found = findLease(name).filter(this::ownsLease);
            if (found.isEmpty()) {
                return;
            }
            Lease lease = found.get();
            Instant now = now();
            Instant lockAtLeastUntil = lockConfiguration.getLockAtLeastUntil();
            Instant until = lockAtLeastUntil.isAfter(now) ? lockAtLeastUntil : now;
            try {
                leases().resource(update(lease, until, now, false)).update();
                logger.debug("Unlocked {}", lockConfiguration);
            } catch (KubernetesClientException e) {
                if (!isConflict(e)) {
                    throw e;
                }
            }
        }

        @Override
        protected Optional<SimpleLock> doExtend(LockConfiguration newConfiguration) {
            Instant now = now();
            Optional<Lease> active = findLease(newConfiguration.getName())
                    .filter(this::ownsLease)
                    .filter(found -> lockedUntil(found).isAfter(now));
            if (active.isEmpty()) {
                return Optional.empty();
            }
            Lease lease = active.get();
            Lease extended;
            try {
                extended = leases().resource(update(lease, newConfiguration.getLockAtMostUntil(), now, false))
                        .update();
            } catch (KubernetesClientException e) {
                if (isConflict(e)) {
                    return Optional.empty();
                }
                throw e;
            }
            logger.debug("Extended {}", newConfiguration);
            return Optional.of(new LeaseLock(newConfiguration, extended));
        }
    }
}
