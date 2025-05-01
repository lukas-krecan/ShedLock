/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.provider.redis.lettuce;

import com.playtika.testcontainer.redis.RedisProperties;
import com.playtika.testcontainer.redis.wait.RedisClusterStatusCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.utility.MountableFile;

class RedisContainer extends FixedHostPortGenericContainer<RedisContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisContainer.class);

    static final int PORT = 6379;

    static final String ENV = "test";

    public RedisContainer(int hostPort) {
        super("redis:7.2.3-alpine");

        RedisProperties properties = new RedisProperties();
        properties.host = "localhost";
        properties.port = hostPort;
        properties.requirepass = false;

        this.withFixedExposedPort(hostPort, PORT)
                .withExposedPorts(PORT)
                .withLogConsumer(frame -> LOGGER.info(frame.getUtf8String()))
                .withCopyFileToContainer(MountableFile.forClasspathResource("redis.conf"), "/data/redis.conf")
                .withCopyFileToContainer(MountableFile.forClasspathResource("nodes.conf"), "/data/nodes.conf")
                .waitingFor(new RedisClusterStatusCheck(properties))
                // .waitingFor(new RedisStatusCheck(properties))
                .withCommand("redis-server", "/data/redis.conf");
    }
}
