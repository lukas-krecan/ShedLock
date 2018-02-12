/**
 * Copyright 2009-2017 the original author or authors.
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
import org.apache.curator.utils.PathUtils;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.time.Instant;
import java.util.Optional;

import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

/**
 * Locks kept using ZooKeeper. When locking, creates an ephemeral node with node name = lock name, when unlocking, removes the node.
 */
public class ZookeeperCuratorLockProvider implements LockProvider {
    public static final String DEFAULT_PATH = "/shedlock";
    private final String path;
    private final CuratorFramework client;

    public ZookeeperCuratorLockProvider(CuratorFramework client) {
        this(client, DEFAULT_PATH);
    }

    public ZookeeperCuratorLockProvider(CuratorFramework client, String path) {
        this.client = requireNonNull(client);
        this.path = PathUtils.validatePath(path);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        Instant now = now();
        if (lockConfiguration.getLockAtLeastUntil().isAfter(now)) {
            throw new UnsupportedOperationException("ZookeeperCuratorLockProvider does not support nonzero lockAtLeastUntil yet.");
        }
        try {
            String nodePath = getNodePath(lockConfiguration);
            client.create().creatingParentsIfNeeded().withMode(CreateMode.EPHEMERAL).forPath(nodePath);
            return Optional.of(new CuratorLock(nodePath, client));
        } catch (KeeperException.NodeExistsException ex) {
            return Optional.empty();
        } catch (Exception e) {
            throw new LockException("Can not create node", e);
        }
    }

    private String getNodePath(LockConfiguration lockConfiguration) {
        return path + "/" + lockConfiguration.getName();
    }

    private static final class CuratorLock implements SimpleLock {
        private final String nodePath;
        private final CuratorFramework client;

        private CuratorLock(String nodePath, CuratorFramework client) {
            this.nodePath = nodePath;
            this.client = client;
        }

        @Override
        public void unlock() {
            try {
                client.delete().forPath(nodePath);
            } catch (Exception e) {
                throw new LockException("Can not remove node", e);
            }
        }
    }
}
