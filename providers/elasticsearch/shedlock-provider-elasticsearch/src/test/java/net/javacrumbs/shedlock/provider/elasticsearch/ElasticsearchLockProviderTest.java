package net.javacrumbs.shedlock.provider.elasticsearch;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.test.support.AbstractLockProviderIntegrationTest;
import org.apache.http.HttpHost;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import pl.allegro.tech.embeddedelasticsearch.EmbeddedElastic;
import pl.allegro.tech.embeddedelasticsearch.IndexSettings;
import pl.allegro.tech.embeddedelasticsearch.PopularProperties;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Map;

import static java.lang.ClassLoader.getSystemResourceAsStream;
import static java.util.concurrent.TimeUnit.MINUTES;
import static net.javacrumbs.shedlock.provider.elasticsearch.ElasticsearchLockProvider.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class ElasticsearchLockProviderTest extends AbstractLockProviderIntegrationTest {

    private static EmbeddedElastic embeddedElastic;
    private RestHighLevelClient highLevelClient;
    private ElasticsearchLockProvider lockProvider;

    @Before
    public void setUp() {
        highLevelClient = new RestHighLevelClient(
                RestClient.builder(
                        new HttpHost("localhost", 9350, "http")));
        lockProvider = new ElasticsearchLockProvider(highLevelClient);
    }

    @After
    public void tearDown() throws IOException {
        highLevelClient.close();
    }

    @Override
    protected LockProvider getLockProvider() {
        return lockProvider;
    }

    @Override
    protected void assertUnlocked(String lockName) {
        GetRequest gr = new GetRequest(SCHEDLOCK_DEFAULT_INDEX, SCHEDLOCK_DEFAULT_TYPE, lockName);
        try {
            GetResponse res = highLevelClient.get(gr, RequestOptions.DEFAULT);
            Map<String, Object> m = res.getSourceAsMap();
            assertThat(new Date((Long) m.get(LOCK_UNTIL))).isBeforeOrEqualsTo(now());
            assertThat(new Date((Long) m.get(LOCKED_AT))).isBeforeOrEqualsTo(now());
            assertThat((String) m.get(LOCKED_BY)).isNotBlank();
            assertThat((String) m.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    @Override
    protected void assertLocked(String lockName) {
        GetRequest gr = new GetRequest(SCHEDLOCK_DEFAULT_INDEX, SCHEDLOCK_DEFAULT_TYPE, lockName);
        try {
            GetResponse res = highLevelClient.get(gr, RequestOptions.DEFAULT);
            Map<String, Object> m = res.getSourceAsMap();
            assertThat(new Date((Long) m.get(LOCK_UNTIL))).isAfter(now());
            assertThat(new Date((Long) m.get(LOCKED_AT))).isBeforeOrEqualsTo(now());
            assertThat((String) m.get(LOCKED_BY)).isNotBlank();
            assertThat((String) m.get(NAME)).isEqualTo(lockName);
        } catch (IOException e) {
            fail("Call to embedded ES failed.");
        }
    }

    @BeforeClass
    public static void startEmbeddedElastic() throws IOException, InterruptedException {
        embeddedElastic = EmbeddedElastic.builder()
                .withElasticVersion("6.4.0")
                .withSetting(PopularProperties.HTTP_PORT, 9350)
                .withSetting(PopularProperties.CLUSTER_NAME, "my_cluster")
                .withStartTimeout(2, MINUTES)
                .withIndex(SCHEDLOCK_DEFAULT_INDEX, IndexSettings.builder()
                        .withType("lock", getSystemResourceAsStream("shedlock.mapping.json"))
                        .withSettings(getSystemResourceAsStream("shedlock.settings.json"))
                        .build())
                .build()
                .start();
    }

    @AfterClass
    public static void stopEmbeddedElastic() {
        if (embeddedElastic != null) {
            embeddedElastic.stop();
        }
    }

    private Date now() {
        return new Date();
    }
}