package net.javacrumbs.shedlock.provider.commercetools;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.defaultconfig.ApiRootBuilder;
import com.commercetools.api.models.custom_object.CustomObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vrap.rmf.base.client.oauth2.ClientCredentials;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CommercetoolsLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    static final int COMMERCETOOLS_DEFAULT_PORT = 8989;

    @Container
    private static final CommercetoolsContainer container = new CommercetoolsContainer();

    private static ProjectApiRoot projectApiRoot;
    private static ObjectMapper objectMapper;

    @BeforeAll
    static void setUp() {
        objectMapper = JsonUtils.createObjectMapper();
        projectApiRoot = ApiRootBuilder.of()
            .defaultClient(
                ClientCredentials.of()
                    .withClientId("myId")
                    .withClientSecret("mySecret")
                    .build(),
                container.getApiBaseUrl() + "/oauth/token",
                container.getApiBaseUrl())
            .build("my-project");
    }

    @Override
    protected LockProvider getLockProvider() {
        return new CommercetoolsLockProvider(projectApiRoot);
    }

    @Override
    protected void assertUnlocked(String lockName) {
        CustomObject responseBody = readLockValue(lockName);
        LockValue lockValue = objectMapper.convertValue(responseBody.getValue(), LockValue.class);
        assertThat(lockValue.lockUntil()).isBeforeOrEqualTo((now()));
        assertThat(lockValue.lockedAt()).isBeforeOrEqualTo(now());
        assertThat(lockValue.lockedBy()).isNotBlank();
        assertThat(responseBody.getKey()).isEqualTo(lockName);
    }

    @Override
    protected void assertLocked(String lockName) {
        CustomObject responseBody = readLockValue(lockName);
        LockValue lockValue = objectMapper.convertValue(responseBody.getValue(), LockValue.class);
        assertThat(lockValue.lockUntil()).isAfter((now()));
        assertThat(lockValue.lockedAt()).isBeforeOrEqualTo(now());
        assertThat(lockValue.lockedBy()).isNotBlank();
        assertThat(responseBody.getKey()).isEqualTo(lockName);
    }

    private CustomObject readLockValue(String lockName) {
        return projectApiRoot
            .customObjects()
            .withContainerAndKey("lock", lockName)
            .get()
            .executeBlocking()
            .getBody();
    }

    private static class CommercetoolsContainer extends GenericContainer<CommercetoolsContainer> {
        CommercetoolsContainer() {
            super("labdigital/commercetools-mock-server");
            withExposedPorts(COMMERCETOOLS_DEFAULT_PORT);
            setImage(prepareImage(getDockerImageName()));
        }

        private ImageFromDockerfile prepareImage(String imageName) {
            return new ImageFromDockerfile().withDockerfileFromBuilder(builder -> builder.from(imageName));
        }

        public String getApiBaseUrl() {
            return "http://" + getHost() + ":" + getFirstMappedPort();
        }
    }
}
