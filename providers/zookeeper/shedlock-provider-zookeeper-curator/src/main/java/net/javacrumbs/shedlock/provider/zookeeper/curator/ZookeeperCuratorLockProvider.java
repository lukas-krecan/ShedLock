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
package net.javacrumbs.shedlock.provider.zookeeper.curator;

import net.javacrumbs.shedlock.core.AbstractSimpleLock;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.utils.PathUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Locks kept using ZooKeeper. When locking, creates a PERSISTENT  node with node name = lock_name and value containing lock data,
 * when unlocking, keeps the node and changes node data to release the lock.
 */
public class ZookeeperCuratorLockProvider implements LockProvider {
    public static final String DEFAULT_PATH = "/shedlock";
    private final String path;
    private final CuratorFramework client;

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperCuratorLockProvider.class);

    public ZookeeperCuratorLockProvider(@NonNull CuratorFramework client) {
        this(client, DEFAULT_PATH);
    }

    public ZookeeperCuratorLockProvider(@NonNull CuratorFramework client, @NonNull String path) {
        this.client = requireNonNull(client);
        this.path = PathUtils.validatePath(path);
    }

    @Override
    @NonNull
    public Optional<SimpleLock> lock(@NonNull LockConfiguration lockConfiguration) {
        String nodePath = getNodePath(lockConfiguration.getName());

        try {
            Stat stat = new Stat();
            byte[] data = client.getData().storingStatIn(stat).forPath(nodePath);
            if (isLocked(data)) {
                return Optional.empty();
            } else {
                return tryLock(lockConfiguration, nodePath, stat);
            }
        } catch (KeeperException.NoNodeException e) {
            // node does not exists
            if (createNode(lockConfiguration, nodePath)) {
                return Optional.of(new CuratorLock(nodePath, client, lockConfiguration));
            } else {
                logger.trace("Node not created, must have been created by a parallel process");
                return Optional.empty();
            }
        } catch (Exception e) {
            throw new LockException("Can not obtain lock node", e);
        }
    }

    private Optional<SimpleLock> tryLock(LockConfiguration lockConfiguration, String nodePath, Stat stat) throws Exception {
        try {
            client.setData().withVersion(stat.getVersion()).forPath(nodePath, serialize(lockConfiguration.getLockAtMostUntil()));
            return Optional.of(new CuratorLock(nodePath, client, lockConfiguration));
        } catch (KeeperException.BadVersionException e) {
            logger.trace("Node value can not be set, must have been set by a parallel process");
            return Optional.empty();
        }
    }

    private boolean createNode(LockConfiguration lockConfiguration, String nodePath) {
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(nodePath, serialize(lockConfiguration.getLockAtMostUntil()));
            return true;
        } catch (KeeperException.NodeExistsException e) {
            return false;
        } catch (Exception e) {
            throw new LockException("Can not create node", e);
        }
    }

    boolean isLocked(String nodePath) throws Exception {
        byte[] data = client.getData().forPath(nodePath);
        return isLocked(data);
    }

    private boolean isLocked(byte[] data) {
        if (data == null || data.length == 0) {
            // most likely created by previous version of the library
            return true;
        }
        try {
            Instant lockedUntil = parse(data);
            return lockedUntil.isAfter(ClockProvider.now());
        } catch (DateTimeParseException e) {
            // most likely created by previous version of the library
            logger.debug("Can not parse date", e);
            return true;
        }
    }

    private static byte[] serialize(Instant date) {
        return toIsoString(date).getBytes(UTF_8);
    }

    private static Instant parse(byte[] data) {
        return Instant.parse(new String(data, UTF_8));
    }

    String getNodePath(String lockName) {
        return path + "/" + lockName;
    }

    private static final class CuratorLock extends AbstractSimpleLock {
        private final String nodePath;
        private final CuratorFramework client;

        private CuratorLock(String nodePath, CuratorFramework client, LockConfiguration lockConfiguration) {
            super(lockConfiguration);
            this.nodePath = nodePath;
            this.client = client;
        }

        @Override
        public void doUnlock() {
            try {
                Instant unlockTime = lockConfiguration.getUnlockTime();
                client.setData().forPath(nodePath, serialize(unlockTime));
            } catch (Exception e) {
                throw new LockException("Can not remove node", e);
            }
        }
    }
}
