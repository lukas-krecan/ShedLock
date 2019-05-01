/**
 * Copyright 2009-2018 the original author or authors.
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

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.framework.recipes.locks.Locker;
import org.apache.curator.utils.PathUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static net.javacrumbs.shedlock.support.Utils.toIsoString;

/**
 * Locks kept using ZooKeeper. Uses node "/shedlock/lockName" to store lock data and InterProcessMutex in path
 * "/shedlock/lockName_shedlock_sync" to prevent multiple processes to update lock data.
 */
public class ZookeeperCuratorLockProvider implements LockProvider {
    public static final String DEFAULT_PATH = "/shedlock";
    private final String path;
    private final CuratorFramework client;

    private static final Logger logger = LoggerFactory.getLogger(ZookeeperCuratorLockProvider.class);

    public ZookeeperCuratorLockProvider(CuratorFramework client) {
        this(client, DEFAULT_PATH);
    }

    public ZookeeperCuratorLockProvider(CuratorFramework client, String path) {
        this.client = requireNonNull(client);
        this.path = PathUtils.validatePath(path);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        String nodePath = getNodePath(lockConfiguration.getName());
        // prevents multiple processes to set the same lock
        InterProcessMutex lock = new InterProcessMutex(client, nodePath + "_shedlock_sync");
        try (Locker locker = new Locker(lock)) {
            if (!isLocked(nodePath)) {
                client.setData().forPath(nodePath, serialize(lockConfiguration.getLockAtMostUntil()));
                return Optional.of(new CuratorLock(nodePath, client, lockConfiguration));
            } else {
                return Optional.empty();
            }
        } catch (KeeperException.NoNodeException ex) {
            // node does not exists
            createNode(lockConfiguration, nodePath);
            return Optional.of(new CuratorLock(nodePath, client, lockConfiguration));
        } catch (Exception e) {
            throw new LockException("Can not obtain lock", e);
        }
    }

    private void createNode(LockConfiguration lockConfiguration, String nodePath) {
        try {
            client.create().creatingParentsIfNeeded().withMode(CreateMode.PERSISTENT).forPath(nodePath, serialize(lockConfiguration.getLockAtMostUntil()));
        } catch (Exception e) {
            throw new LockException("Can not create node", e);
        }
    }

    boolean isLocked(String nodePath) throws Exception {
        byte[] data = client.getData().forPath(nodePath);
        if (data == null || data.length == 0) {
            // most likely created by previous version of the library
            return true;
        }
        try {
            Instant lockedUntil = parse(data);
            return lockedUntil.isAfter(Instant.now());
        } catch (DateTimeParseException e) {
            // most likely created by previous version of the library
            logger.debug("Can not parse date", e);
            return true;
        }
    }

    private static byte[] serialize(Instant date) {
        return toIsoString(date).getBytes();
    }

    private static Instant parse(byte[] data) {
        return Instant.parse(new String(data));
    }

    String getNodePath(String lockName) {
        return path + "/" + lockName;
    }

    private static final class CuratorLock implements SimpleLock {
        private final String nodePath;
        private final CuratorFramework client;
        private final LockConfiguration lockConfiguration;

        private CuratorLock(String nodePath, CuratorFramework client, LockConfiguration lockConfiguration) {
            this.nodePath = nodePath;
            this.client = client;
            this.lockConfiguration = lockConfiguration;
        }

        @Override
        public void unlock() {
            try {
                Instant unlockTime = lockConfiguration.getUnlockTime();
                client.setData().forPath(nodePath, serialize(unlockTime));
            } catch (Exception e) {
                throw new LockException("Can not remove node", e);
            }
        }
    }
}
