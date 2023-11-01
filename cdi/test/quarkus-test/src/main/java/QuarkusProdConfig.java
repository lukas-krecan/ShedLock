import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.util.Optional;
import net.javacrumbs.shedlock.core.LockProvider;

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
