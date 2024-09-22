package net.javacrumbs.shedlock.provider.commercetools;

import static java.time.temporal.ChronoUnit.MINUTES;
import static net.javacrumbs.shedlock.core.ClockProvider.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import com.commercetools.api.defaultconfig.ApiRootBuilder;
import io.vrap.rmf.base.client.oauth2.ClientCredentials;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.Times;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class CommercetoolsLockProviderIntegrationTest {
    private String lockName1;
    public static final DockerImageName MOCKSERVER_IMAGE = DockerImageName
        .parse("mockserver/mockserver")
        .withTag("mockserver-" + MockServerClient.class.getPackage().getImplementationVersion());

    @Container
    public MockServerContainer mockServer = new MockServerContainer(MOCKSERVER_IMAGE);

    private MockServerClient mockServerClient;
    private String mockServerHostAndPort;

    @BeforeEach
    void setUp() throws IOException {
        lockName1 = UUID.randomUUID().toString();
        mockServerHostAndPort = "http://" + mockServer.getHost() + ":" + mockServer.getServerPort();
        mockServerClient = new MockServerClient(mockServer.getHost(), mockServer.getServerPort());
        mockServerClient
            .when(request().withPath("/auth"))
            .respond(response().withBody(readResponseFromFile("./token.json")));
    }


    @AfterEach
    void tearDown() {
        mockServerClient.reset();
        mockServerClient.close();
    }

    @Test
    public void shouldCreateLock() throws IOException {
        Instant now = now();
        mockRequestLockNotFound(lockName1);
        mockRequestLockCreateOrUpdate();
        mockRequestLockFound(lockName1, now, now.plusSeconds(10));

        Optional<SimpleLock> lock = getLockProvider().lock(lockConfig(lockName1));
        assertThat(lock).isNotEmpty();

        assertLocked(lockName1);
        lock.get().unlock();
        assertUnlocked(lockName1);
    }

    @Test
    public void shouldNotReturnSecondLock() throws IOException {
        Instant now = now();
        mockRequestLockFound(lockName1, now, now.plusSeconds(10));

        CommercetoolsLockProvider lockProvider = getLockProvider();
        assertThat(lockProvider.lock(lockConfig(lockName1))).isEmpty();
    }

    @Test
    public void shouldCreateTwoIndependentLocks() throws IOException {
        mockRequestLockNotFound(lockName1);
        mockRequestLockNotFound("name2");
        mockRequestLockCreateOrUpdate();

        Optional<SimpleLock> lock1 = getLockProvider().lock(lockConfig(lockName1));
        assertThat(lock1).isNotEmpty();

        Optional<SimpleLock> lock2 = getLockProvider().lock(lockConfig("name2"));
        assertThat(lock2).isNotEmpty();
    }


    private void mockRequestLockFound(String lockName, Instant lockedAt, Instant lockUntil) throws IOException {
        mockServerClient
            .when(request().withMethod("GET").withPath("/integration-test/custom-objects/lock/" + lockName), Times.exactly(1))
            .respond(response()
                .withBody(readResponseFromFile("./lock.json")
                    .replace("$lockUntil", lockUntil.toString())
                    .replace("$lockedAt", lockedAt.toString())));
    }

    private void mockRequestLockCreateOrUpdate() throws IOException {
        mockServerClient
            .when(request().withMethod("POST").withPath("/integration-test/custom-objects"))
            .respond(response().withBody(readResponseFromFile("./lock.json")));
    }

    private void mockRequestLockNotFound(String lockName) throws IOException {
        mockServerClient
            .when(request().withMethod("GET").withPath("/integration-test/custom-objects/lock/" + lockName), Times.exactly(1))
            .respond(response()
                .withStatusCode(404)
                .withBody(readResponseFromFile("./not-found.json")));
    }

    private CommercetoolsLockProvider getLockProvider() {
        return new CommercetoolsLockProvider(ApiRootBuilder.of()
            .defaultClient(ClientCredentials.of()
                .withClientId("test")
                .withClientSecret("test")
                .build(), mockServerHostAndPort + "/auth", mockServerHostAndPort)
            .build("integration-test"));
    }

    private void assertUnlocked() {
        mockServerClient.verify(request().withPath("/integration-test/custom-objects"));
    }

    private void assertLocked() {
        mockServerClient.verify(request().withPath("/integration-test/custom-objects"));
    }

    private String readResponseFromFile(String filename) throws IOException {
        final File file = new File(getClass().getClassLoader().getResource(filename).getFile());
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }

    private static LockConfiguration lockConfig(String name) {
        return lockConfig(name, Duration.of(5, MINUTES), Duration.ZERO);
    }

    private static LockConfiguration lockConfig(String name, Duration lockAtMostFor, Duration lockAtLeastFor) {
        return new LockConfiguration(now(), name, lockAtMostFor, lockAtLeastFor);
    }
}
