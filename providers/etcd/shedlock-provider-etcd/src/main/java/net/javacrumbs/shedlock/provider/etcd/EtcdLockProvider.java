/**
 * Copyright 2009-2021 the original author or authors.
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
package net.javacrumbs.shedlock.provider.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.Lease;
import io.etcd.jetcd.Txn;
import io.etcd.jetcd.kv.TxnResponse;
import io.etcd.jetcd.lease.LeaseGrantResponse;
import io.etcd.jetcd.op.Cmp;
import io.etcd.jetcd.op.CmpTarget;
import io.etcd.jetcd.op.Op;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

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

    private static final String KEY_PREFIX = "job-lock";
    // prepared multi-environment support
    static final String ENV_DEFAULT = "default";

    private final EtcdTemplate etcdTemplate;

    public EtcdLockProvider(@NonNull Client client) {
        this.etcdTemplate = new EtcdTemplate(client);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String key = buildKey(lockConfiguration.getName(), ENV_DEFAULT);
        return new EtcdLock(key, etcdTemplate, lockConfiguration).lease();
    }

    private static final class EtcdLock extends AbstractSimpleLock {
        private static final BigDecimal MILLIS_IN_SECOND = BigDecimal.valueOf(1000);
        private final String key;
        private Long successLeaseId;
        private final EtcdTemplate etcdTemplate;

        private EtcdLock(String key, EtcdTemplate etcdTemplate, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.key = key;
            this.etcdTemplate = etcdTemplate;
        }

        EtcdLock successLeaseId(Long successLeaseId) {
            this.successLeaseId = successLeaseId;
            return this;
        }

        Optional<SimpleLock> lease() {
            Long leaseId = etcdTemplate.createLease(getSecondsUntil(lockConfiguration.getLockAtMostUntil()));

            Optional<Long> lockSuccess = etcdTemplate.tryToLock(key, buildValue(), leaseId);
            if (lockSuccess.isPresent()) {
                return lockSuccess.map(this::successLeaseId);
            } else {
                etcdTemplate.revoke(leaseId);
                return Optional.empty();
            }
        }

        @Override
        public void doUnlock() {
            long keepLockFor = getSecondsUntil(lockConfiguration.getLockAtLeastUntil());

            // lock at least until is in the past
            if (keepLockFor <= 0) {
                try {
                    etcdTemplate.revoke(successLeaseId);
                } catch (Exception e) {
                    throw new LockException("Can not revoke old leaseId " + successLeaseId, e);
                }
            } else {
                // implement lockAtLeast functionality with a new leaseId
                Long leaseId = etcdTemplate.createLease(keepLockFor);

                etcdTemplate.putWithLeaseId(key, buildValue(), leaseId);
                this.successLeaseId = leaseId;
            }
        }

        private long getSecondsUntil(Instant instant) {
            return BigDecimal.valueOf(getMsUntil(instant))
                .divide(MILLIS_IN_SECOND, RoundingMode.CEILING)
                .longValue();
        }

        private long getMsUntil(Instant instant) {
            return Duration.between(ClockProvider.now(), instant).toMillis();
        }

        private String buildValue() {
            return String.format("ADDED:%s@%s", toIsoString(ClockProvider.now()), getHostname());
        }

    }

    static String buildKey(String lockName, String env) {
        return String.format("%s:%s:%s", KEY_PREFIX, env, lockName);
    }

    private static class EtcdTemplate {
        private final KV kvClient;
        private final Lease leaseClient;

        private EtcdTemplate(Client client) {
            this.kvClient = client.getKVClient();
            this.leaseClient = client.getLeaseClient();
        }

        public Long createLease(long lockUntilInSeconds) {
            CompletableFuture<LeaseGrantResponse> leaseGrantResp = leaseClient.grant(lockUntilInSeconds);
            try {
                return leaseGrantResp.get().getID();
            } catch (Exception e) {
                throw new LockException("Failed create lease", e);
            }
        }

        public Optional<Long> tryToLock(String key, String value, Long leaseId) {
            try {
                ByteSequence lockKey = ByteSequence.from(key.getBytes());

                PutOption putOption = PutOption.newBuilder()
                    .withLeaseId(leaseId)
                    .build();

                GetOption getOption = GetOption.DEFAULT;

                // Version is the version of the key.
                // A deletion resets the version to zero and any modification of the key increases its version.
                Txn txn = kvClient.txn()
                    .If(new Cmp(lockKey, Cmp.Op.EQUAL, CmpTarget.version(0)))
                    .Then(Op.put(lockKey, ByteSequence.from(value.getBytes()), putOption))
                    .Else(Op.get(lockKey, getOption));
                CompletableFuture<TxnResponse> txnRespFuture = txn.commit();

                TxnResponse tr = txnRespFuture.get();
                if (tr.isSucceeded()) {
                    return Optional.of(leaseId);
                }
            } catch (Exception e) {
                throw new LockException("Failed to set lock " + key, e);
            }
            return Optional.empty();
        }

        public void revoke(Long leaseId) {
            try {
                leaseClient.revoke(leaseId);
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
            ByteSequence lockKey = ByteSequence.from(key.getBytes());
            ByteSequence lockValue = ByteSequence.from(value.getBytes());

            PutOption putOption = PutOption.newBuilder()
                .withLeaseId(leaseId)
                .build();
            kvClient.put(lockKey, lockValue, putOption);
        }
    }

}
