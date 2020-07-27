package net.javacrumbs.shedlock.provider.redis.jedis;

import com.playtika.test.redis.RedisProperties;
import com.playtika.test.redis.wait.RedisClusterStatusCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.utility.MountableFile;

class RedisContainer extends FixedHostPortGenericContainer<RedisContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisContainer.class);

    final static int PORT = 6379;

    final static String ENV = "test";


    public RedisContainer(int hostPort) {
        super("redis:5-alpine");

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
            //.waitingFor(new RedisStatusCheck(properties))
            .withCommand("redis-server", "/data/redis.conf");
    }
}
