package net.javacrumbs.shedlock.provider.memcached;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.FixedHostPortGenericContainer;
import org.testcontainers.utility.DockerImageName;

public class MemcachedContainer extends FixedHostPortGenericContainer<MemcachedContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemcachedContainer.class);

    public static final DockerImageName MEMCACHED_IMAGE = DockerImageName.parse("memcached:latest");

    final static int PORT = 11211;

    public MemcachedContainer(int hostPort) {
        super(MEMCACHED_IMAGE.asCanonicalNameString());
        this.withFixedExposedPort(hostPort, PORT)
            .withExposedPorts(PORT)
            .withLogConsumer(frame -> LOGGER.info(frame.getUtf8String()));
    }

}
