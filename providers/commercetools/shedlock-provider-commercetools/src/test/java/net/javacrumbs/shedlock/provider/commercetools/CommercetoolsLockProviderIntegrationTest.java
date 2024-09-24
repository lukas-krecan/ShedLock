package net.javacrumbs.shedlock.provider.commercetools;

import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static net.javacrumbs.shedlock.provider.commercetools.CommercetoolsLockProvider.LOCK_CONTAINER;
import static org.assertj.core.api.Assertions.assertThat;

import com.commercetools.api.client.ProjectApiRoot;
import com.commercetools.api.defaultconfig.ApiRootBuilder;
import com.commercetools.api.models.custom_object.CustomObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vrap.rmf.base.client.AuthenticationToken;
import io.vrap.rmf.base.client.utils.json.JsonUtils;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CommercetoolsLockProviderIntegrationTest extends AbstractLockProviderIntegrationTest {
    static final int COMMERCETOOLS_DEFAULT_PORT = 8989;

    @Container
    private static final CommercetoolsContainer container = new CommercetoolsContainer();

    private ProjectApiRoot projectApiRoot;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonUtils.createObjectMapper();
        var token = new AuthenticationToken();
        token.setTokenType("Bearer");
        token.setExpiresIn(172800L);
        token.setAccessToken("P7K6uFOgoWBdlwj6nO08Dg==");
        projectApiRoot = ApiRootBuilder.of()
            .withStaticTokenFlow(token)
            .withApiBaseUrl(container.getApiBaseUrl())
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
            .withContainerAndKey(LOCK_CONTAINER, lockName)
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
