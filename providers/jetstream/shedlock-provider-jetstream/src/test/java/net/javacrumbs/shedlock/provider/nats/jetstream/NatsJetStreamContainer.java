package net.javacrumbs.shedlock.provider.nats.jetstream;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class NatsJetStreamContainer extends GenericContainer<NatsJetStreamContainer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(NatsJetStreamContainer.class);

    public static final DockerImageName NATS_IMAGE = DockerImageName.parse("nats:2.11-alpine");

    private static final Integer NATS_PORT = 4222;

    public NatsJetStreamContainer() {
        super(NATS_IMAGE.asCanonicalNameString());
        this.withExposedPorts(NATS_PORT)
                .withNetworkAliases("nats")
                .withLogConsumer(frame -> LOGGER.info(frame.getUtf8String().replace("\n", "")))
                .withCommand("--jetstream")
                .waitingFor(new LogMessageWaitStrategy().withRegEx(".*Server is ready.*"))
                .withStartupTimeout(Duration.ofSeconds(30L));
    }
}
