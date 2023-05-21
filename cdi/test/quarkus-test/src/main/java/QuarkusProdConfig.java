import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import java.util.Optional;

/*
 * Just to make the build to pass
 */
public class QuarkusProdConfig {
    @Produces
    @Singleton
    @DefaultBean
    public LockProvider lockProvider() {
        return lockConfiguration -> Optional.empty();
    }
}
