/**
 * Copyright 2009-2016 the original author or authors.
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
package net.javacrumbs.shedlock.provider.zookeeper.curator;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import net.javacrumbs.shedlock.support.LockException;
import org.apache.curator.framework.CuratorFramework;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;

import java.util.Optional;

import static java.util.Objects.requireNonNull;

/**
 * Locks kept using ZooKeeper. When locking, creates an ephemeral node with node name = lock name, when nlocking, removes the node.
 */
public class ZookeeperCuratorLockProvider implements LockProvider {
    private final CuratorFramework client;

    public ZookeeperCuratorLockProvider(CuratorFramework client) {
        this.client = requireNonNull(client);
    }

    @Override
    public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
        try {
            client.create().withMode(CreateMode.EPHEMERAL).forPath(getNodeName(lockConfiguration));
            return Optional.of(new CuratorLock(lockConfiguration, client));
        } catch (KeeperException.NodeExistsException ex) {
            return Optional.empty();
        } catch (Exception e) {
            throw new LockException("Can not create node", e);
        }
    }

    private static String getNodeName(LockConfiguration lockConfiguration) {
        return "/" + lockConfiguration.getName();
    }

    private static final class CuratorLock implements SimpleLock {
        private final LockConfiguration configuration;
        private final CuratorFramework client;

        private CuratorLock(LockConfiguration configuration, CuratorFramework client) {
            this.configuration = configuration;
            this.client = client;
        }

        @Override
        public void unlock() {
            try {
                client.delete().forPath(getNodeName(configuration));
            } catch (Exception e) {
                throw new LockException("Can not remove node", e);
            }
        }
    }
}
