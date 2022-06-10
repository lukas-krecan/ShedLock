package net.javacrumbs.container;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.Base58;

import java.time.Duration;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

public class OpenSearchContainer extends GenericContainer<OpenSearchContainer> {

    static final int OPENSEARCH_DEFAULT_PORT = 9200;
    static final int OPENSEARCH_DEFAULT_TCP_PORT = 9300;
    public OpenSearchContainer(String dockerImageName) {
        super(dockerImageName);
    }

    private ImageFromDockerfile prepareImage(String imageName) {
        return new ImageFromDockerfile()
            .withDockerfileFromBuilder(builder -> {
                builder.from(imageName);
            });
    }

    @Override
    protected void configure() {
        withNetworkAliases("opensearch-" + Base58.randomString(6));
        withEnv("discovery.type", "single-node");
        addExposedPorts(OPENSEARCH_DEFAULT_PORT, OPENSEARCH_DEFAULT_TCP_PORT);
        setWaitStrategy(new HttpWaitStrategy()
            .forPort(OPENSEARCH_DEFAULT_PORT)
            .forStatusCodeMatching(response -> response == HTTP_OK || response == HTTP_UNAUTHORIZED)
            .withStartupTimeout(Duration.ofMinutes(2)));
        setImage(prepareImage(getDockerImageName()));
    }

    public String getHttpHostAddress() {
        return getHost() + ":" + getMappedPort(OPENSEARCH_DEFAULT_PORT);
    }
}
