/**
 * Copyright 2009 the original author or authors.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Optional;
import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.expression.BeanFactoryAccessor;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.KotlinDetector;
import org.springframework.core.KotlinReflectionParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PrioritizedParameterNameDiscoverer;
import org.springframework.core.StandardReflectionParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

class SpringLockConfigurationExtractor implements ExtendedLockConfigurationExtractor {
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    private static final ParserContext PARSER_CONTEXT = new TemplateParserContext();
    public static final PrioritizedParameterNameDiscoverer PARAMETER_NAME_DISCOVERER =
            new PrioritizedParameterNameDiscoverer();
    private final Duration defaultLockAtMostFor;
    private final Duration defaultLockAtLeastFor;

    @Nullable
    private final StringValueResolver embeddedValueResolver;

    @Nullable
    private final BeanFactory beanFactory;

    private final StandardEvaluationContext originalEvaluationContext = new StandardEvaluationContext();

    private final Converter<String, Duration> durationConverter;
    private final Logger logger = LoggerFactory.getLogger(SpringLockConfigurationExtractor.class);

    static {
        if (KotlinDetector.isKotlinReflectPresent()) {
            PARAMETER_NAME_DISCOVERER.addDiscoverer(new KotlinReflectionParameterNameDiscoverer());
        }
        PARAMETER_NAME_DISCOVERER.addDiscoverer(new SimpleParameterNameDiscoverer());
    }

    public SpringLockConfigurationExtractor(
            Duration defaultLockAtMostFor,
            Duration defaultLockAtLeastFor,
            @Nullable StringValueResolver embeddedValueResolver,
            Converter<String, Duration> durationConverter) {
        this(defaultLockAtMostFor, defaultLockAtLeastFor, embeddedValueResolver, durationConverter, null);
    }

    public SpringLockConfigurationExtractor(
            Duration defaultLockAtMostFor,
            Duration defaultLockAtLeastFor,
            @Nullable StringValueResolver embeddedValueResolver,
            Converter<String, Duration> durationConverter,
            @Nullable BeanFactory beanFactory) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
        this.durationConverter = requireNonNull(durationConverter);
        this.embeddedValueResolver = embeddedValueResolver;
        this.beanFactory = beanFactory;
        originalEvaluationContext.addPropertyAccessor(new BeanFactoryAccessor());
    }

    @Override
    public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
        if (task instanceof ScheduledMethodRunnable scheduledMethodRunnable) {
            return getLockConfiguration(
                    scheduledMethodRunnable.getTarget(), scheduledMethodRunnable.getMethod(), new Object[] {});
        } else {
            logger.debug("Unknown task type {}", task);
        }
        return Optional.empty();
    }

    @Override
    public Optional<LockConfiguration> getLockConfiguration(
            @Nullable Object target, Method method, @Nullable Object[] parameterValues) {
        AnnotationData annotation = findAnnotation(target, method);
        if (annotation != null) {
            return Optional.of(getLockConfiguration(annotation, method, parameterValues));
        } else {
            return Optional.empty();
        }
    }

    private LockConfiguration getLockConfiguration(
            AnnotationData annotation, Method method, @Nullable Object[] parameterValues) {
        return new LockConfiguration(
                ClockProvider.now(),
                getName(annotation, method, parameterValues),
                getLockAtMostFor(annotation),
                getLockAtLeastFor(annotation));
    }

    private String getName(AnnotationData annotation, Method method, @Nullable Object[] parameterValues) {
        String name = parseSpEL(annotation.name(), method, parameterValues);
        if (embeddedValueResolver != null) {
            String resolved = embeddedValueResolver.resolveStringValue(name);
            return resolved != null ? resolved : name;
        } else {
            return name;
        }
    }

    private String parseSpEL(String name, Method method, @Nullable Object[] parameterValues) {
        return getEvaluationContext(method, parameterValues)
                .map(evaluationContext -> EXPRESSION_PARSER
                        .parseExpression(name, PARSER_CONTEXT)
                        .getValue(evaluationContext, String.class))
                .orElse(name);
    }

    private Optional<EvaluationContext> getEvaluationContext(Method method, @Nullable Object[] parameterValues) {
        // Only applying it when the method has parameters. The while code is pretty fragile, let's hope that
        // most of the users do not parametrize their scheduled methods.
        // We need this as embeddedValueResolver does not support parameters. Inspired by CacheEvaluationContextFactory.
        if (method.getParameters().length > 0 && method.getParameters().length == parameterValues.length) {
            StandardEvaluationContext evaluationContext =
                    new MethodBasedEvaluationContext(beanFactory, method, parameterValues, PARAMETER_NAME_DISCOVERER);
            originalEvaluationContext.applyDelegatesTo(evaluationContext);
            return Optional.of(evaluationContext);
        } else {
            return Optional.empty();
        }
    }

    Duration getLockAtMostFor(AnnotationData annotation) {
        return getValue(
                annotation.lockAtMostFor(),
                annotation.lockAtMostForString(),
                this.defaultLockAtMostFor,
                "lockAtMostForString");
    }

    Duration getLockAtLeastFor(AnnotationData annotation) {
        return getValue(
                annotation.lockAtLeastFor(),
                annotation.lockAtLeastForString(),
                this.defaultLockAtLeastFor,
                "lockAtLeastForString");
    }

    private Duration getValue(
            long valueFromAnnotation, String stringValueFromAnnotation, Duration defaultValue, final String paramName) {
        if (valueFromAnnotation >= 0) {
            return Duration.of(valueFromAnnotation, MILLIS);
        } else if (StringUtils.hasText(stringValueFromAnnotation)) {
            if (embeddedValueResolver != null) {
                stringValueFromAnnotation = embeddedValueResolver.resolveStringValue(stringValueFromAnnotation);
            }
            try {
                requireNonNull(stringValueFromAnnotation, "Invalid " + paramName + " value");
                Duration result = durationConverter.convert(stringValueFromAnnotation);
                if (result == null || result.isNegative()) {
                    throw new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation
                            + "\" - cannot set negative duration");
                }
                return result;
            } catch (IllegalStateException nfe) {
                throw new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation
                        + "\" - cannot parse into long nor duration");
            }
        } else {
            return defaultValue;
        }
    }

    @Nullable
    static AnnotationData findAnnotation(@Nullable Object target, Method method) {
        SchedulerLock annotation = findAnnotation(target, method, SchedulerLock.class);
        if (annotation != null) {
            return new AnnotationData(
                    annotation.name(), -1, annotation.lockAtMostFor(), -1, annotation.lockAtLeastFor());
        }
        return null;
    }

    @Nullable
    static <A extends Annotation> A findAnnotation(@Nullable Object target, Method method, Class<A> annotationType) {
        A annotation = AnnotatedElementUtils.getMergedAnnotation(method, annotationType);
        if (annotation != null) {
            return annotation;
        } else if (target != null) {
            // Try to find annotation on proxied class
            Class<?> targetClass = AopUtils.getTargetClass(target);
            try {
                Method methodOnTarget = targetClass.getMethod(method.getName(), method.getParameterTypes());
                return AnnotatedElementUtils.getMergedAnnotation(methodOnTarget, annotationType);
            } catch (NoSuchMethodException e) {
                return null;
            }
        } else {
            return null;
        }
    }

    record AnnotationData(
            String name,
            long lockAtMostFor,
            String lockAtMostForString,
            long lockAtLeastFor,
            String lockAtLeastForString) {}

    /**
     * Not using {@link StandardReflectionParameterNameDiscoverer} as it is calling executable.hasRealParameterData()
     * and it causes a test to fail.
     */
    private static class SimpleParameterNameDiscoverer implements ParameterNameDiscoverer {
        @Override
        @Nullable
        public String @Nullable [] getParameterNames(Method method) {
            return getParameterNames(method.getParameters());
        }

        @Override
        @Nullable
        public String @Nullable [] getParameterNames(Constructor<?> ctor) {
            return getParameterNames(ctor.getParameters());
        }

        @Nullable
        private String @Nullable [] getParameterNames(Parameter[] parameters) {
            String[] parameterNames = new String[parameters.length];
            for (int i = 0; i < parameters.length; i++) {
                Parameter param = parameters[i];
                // Here it differs from StandardReflectionParameterNameDiscoverer
                if (param.getName() == null) {
                    return null;
                }
                parameterNames[i] = param.getName();
            }
            return parameterNames;
        }
    }
}
