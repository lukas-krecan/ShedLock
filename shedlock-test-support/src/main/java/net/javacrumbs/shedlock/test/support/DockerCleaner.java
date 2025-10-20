package net.javacrumbs.shedlock.test.support;

import com.github.dockerjava.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;

public class DockerCleaner {
    private static final Logger logger = LoggerFactory.getLogger(DockerCleaner.class);

    /**
     * There is not enough space in GitHub Actions runners, so we need to remove Docker images after
     * some tests.
     */
    public static void removeImageInCi(String image) {
        if (System.getenv("GITHUB_ACTIONS") != null) {
            try {
                DockerClientFactory.instance()
                        .client()
                        .removeImageCmd(image)
                        .withForce(true)
                        .exec();
                logger.info("Removed Docker image {}", image);
            } catch (NotFoundException e) {
                logger.info("Docker image {} already removed", image);
            } catch (Exception e) {
                logger.warn("Failed to remove Docker image {}", image, e);
            }
        }
    }
}
