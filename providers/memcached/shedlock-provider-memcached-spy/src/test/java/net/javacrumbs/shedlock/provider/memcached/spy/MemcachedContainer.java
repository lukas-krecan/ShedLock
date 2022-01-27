package net.javacrumbs.shedlock.provider.memcached.spy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

public class MemcachedContainer extends GenericContainer<MemcachedContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(MemcachedContainer.class);

    public static final DockerImageName MEMCACHED_IMAGE = DockerImageName.parse("memcached:1.6-alpine");

    public MemcachedContainer() {
        super(MEMCACHED_IMAGE.asCanonicalNameString());
        this.withExposedPorts(11211)
            .withLogConsumer(frame -> LOGGER.info(frame.getUtf8String()));
    }

}
