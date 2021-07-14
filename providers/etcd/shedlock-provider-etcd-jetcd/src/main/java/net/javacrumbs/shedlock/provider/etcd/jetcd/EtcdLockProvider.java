/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.etcd.jetcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.PutOption;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static io.etcd.jetcd.options.GetOption.DEFAULT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static net.javacrumbs.shedlock.support.Utils.getHostname;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Uses etcd keys and the version of the key value pairs as locking mechanism.
 * <p>
 * https://etcd.io/docs/v3.4.0/learning/api/#key-value-pair
 *
 * The timeout is implemented with the lease concept of etcd, which grants a TTL for key value pairs.
 */
public class EtcdLockProvider implements LockProvider {
    private static final double MILLIS_IN_SECOND = 1000;

    private static final String KEY_PREFIX = "shedlock";

    private static final String ENV_DEFAULT = "default";

    private final EtcdTemplate etcdTemplate;

    private final String environment;

    public EtcdLockProvider(@NonNull Client client) {
        this(client, ENV_DEFAULT);
    }

    public EtcdLockProvider(@NonNull Client client, @NonNull String environment) {
        this.etcdTemplate = new EtcdTemplate(client);
        this.environment = environment;
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String key = buildKey(lockConfiguration.getName());
        String value = buildValue();

        Optional<Long> leaseIdOpt = etcdTemplate.tryToLock(key, value, lockConfiguration.getLockAtMostUntil());
        return leaseIdOpt.map(leaseId -> new EtcdLock(key, value, leaseId, etcdTemplate, lockConfiguration));

    }

    private static long getSecondsUntil(Instant instant) {
        return (long) Math.ceil(getMsUntil(instant) / MILLIS_IN_SECOND);
    }

    private static long getMsUntil(Instant instant) {
        return Duration.between(ClockProvider.now(), instant).toMillis();
    }

    private String buildValue() {
        return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
    }

    String buildKey(String lockName) {
        return String.format("%s:%s:%s", KEY_PREFIX, environment, lockName);
    }

    private static final class EtcdLock extends AbstractSimpleLock {
        private final String key;
        private final String value;
        private final Long successLeaseId;
        private final EtcdTemplate etcdTemplate;

        private EtcdLock(String key, String value, Long successLeaseId, EtcdTemplate etcdTemplate, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.value = value;
            this.successLeaseId = successLeaseId;
            this.etcdTemplate = etcdTemplate;
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getSecondsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    // By revoking lease we remove the value and thus release the lock
                    etcdTemplate.revoke(successLeaseId);
                } catch (Exception e) {
                    throw new LockException("Can not revoke old leaseId " + successLeaseId, e);
                }
            } else {
                // implement lockAtLeast functionality with a new leaseId
                Long leaseId = etcdTemplate.createLease(keepLockFor);
                // by putting the key with new shorter lease we change the TTL of the value
                etcdTemplate.putWithLeaseId(key, value, leaseId);
                // revoke the old lease
                etcdTemplate.revoke(this.successLeaseId);
            }
        }
    }

    private static class EtcdTemplate {
        private final KV kvClient;
        private final Lease leaseClient;

        private EtcdTemplate(Client client) {
            this.kvClient = client.getKVClient();
            this.leaseClient = client.getLeaseClient();
        }

        public Long createLease(long lockUntilInSeconds) {
            try {
                return leaseClient.grant(lockUntilInSeconds).get().getID();
            } catch (Exception e) {
                throw new LockException("Failed create lease", e);
            }
        }

        public Optional<Long> tryToLock(String key, String value, Instant lockAtMostUntil) {
            Long leaseId = createLease(getSecondsUntil(lockAtMostUntil));
            try {
                ByteSequence lockKey = toByteSequence(key);
                PutOption putOption = putOptionWithLeaseId(leaseId);

                // Version is the version of the key.
                // A deletion resets the version to zero and any modification of the key increases its version.
                Txn txn = kvClient.txn()
                    .If(new Cmp(lockKey, Cmp.Op.EQUAL, CmpTarget.version(0)))
                    .Then(Op.put(lockKey, toByteSequence(value), putOption))
                    .Else(Op.get(lockKey, DEFAULT));

                TxnResponse tr = txn.commit().get();
                if (tr.isSucceeded()) {
                    return Optional.of(leaseId);
                } else {
                    revoke(leaseId);
                    return Optional.empty();
                }
            } catch (Exception e) {
                revoke(leaseId);
                throw new LockException("Failed to set lock " + key, e);
            }

        }

        public void revoke(Long leaseId) {
            try {
                leaseClient.revoke(leaseId).get();
            } catch (Exception e) {
                throw new LockException("Failed to revoke lease " + leaseId, e);
            }
        }

        /**
         * Set the provided leaseId lease for the key-value pair similar to the CLI command
         * <p>
         * etcdctl put key value --lease <leaseId>
         * <p>
         * If the key has already been put with an other leaseId earlier, the old leaseId
         * will be timed out and then removed, eventually.
         */
        public void putWithLeaseId(String key, String value, Long leaseId) {
            ByteSequence lockKey = toByteSequence(key);
            ByteSequence lockValue = toByteSequence(value);

            PutOption putOption = putOptionWithLeaseId(leaseId);
            kvClient.put(lockKey, lockValue, putOption);
        }

        private ByteSequence toByteSequence(String key) {
            return ByteSequence.from(key.getBytes(UTF_8));
        }

        private PutOption putOptionWithLeaseId(Long leaseId) {
            return PutOption.newBuilder().withLeaseId(leaseId).build();
        }
    }

}
