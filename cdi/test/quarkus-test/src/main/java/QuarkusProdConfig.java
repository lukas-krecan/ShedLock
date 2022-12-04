import io.quarkus.arc.DefaultBean;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;

import javax.enterprise.inject.Produces;
import javax.inject.Singleton;
import java.util.Optional;

/*
 * Just to make the build to pass
 */
public class QuarkusProdConfig {
    @Produces
    @Singleton
    @DefaultBean
    public LockProvider lockProvider() {
        return new LockProvider() {
            @Override
            public Optional<SimpleLock> lock(LockConfiguration lockConfiguration) {
                return Optional.empty();
            }
        };
    }
}
