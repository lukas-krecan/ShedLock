package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.support.annotation.NonNull;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringValueResolver;

import java.time.Duration;

/**
 * Defines ExtendedLockConfigurationExtractor bean.
 */
@Configuration
class LockConfigurationExtractorConfiguration extends AbstractLockConfiguration implements EmbeddedValueResolverAware {
    private final StringToDurationConverter durationConverter = StringToDurationConverter.INSTANCE;

    private StringValueResolver resolver;

    @Bean
    SpringLockConfigurationExtractor lockConfigurationExtractor() {
        return new SpringLockConfigurationExtractor(defaultLockAtMostForDuration(), defaultLockAtLeastForDuration(), resolver, durationConverter);
    }

    private Duration defaultLockAtLeastForDuration() {
        return toDuration(getDefaultLockAtLeastFor());
    }

    private Duration defaultLockAtMostForDuration() {
        return toDuration(getDefaultLockAtMostFor());
    }

    private String getDefaultLockAtLeastFor() {
        return getStringFromAnnotation("defaultLockAtLeastFor");
    }

    private String getDefaultLockAtMostFor() {
        return getStringFromAnnotation("defaultLockAtMostFor");
    }

    private Duration toDuration(String string) {
        return durationConverter.convert(resolver.resolveStringValue(string));
    }

    protected String getStringFromAnnotation(String name) {
        return annotationAttributes.getString(name);
    }

    @Override
    public void setEmbeddedValueResolver(@NonNull StringValueResolver resolver) {
        this.resolver = resolver;
    }
}
