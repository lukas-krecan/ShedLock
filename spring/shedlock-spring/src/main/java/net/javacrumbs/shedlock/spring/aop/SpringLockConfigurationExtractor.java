/**
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.javacrumbs.shedlock.spring.aop;

import net.javacrumbs.shedlock.core.ClockProvider;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.spring.ExtendedLockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import net.javacrumbs.shedlock.support.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.Optional;

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

class SpringLockConfigurationExtractor implements ExtendedLockConfigurationExtractor {
    private static final ExpressionParser EXPRESSION_PARSER = new SpelExpressionParser();
    private static final ParserContext PARSER_CONTEXT = new TemplateParserContext();
    private final Duration defaultLockAtMostFor;
    private final Duration defaultLockAtLeastFor;
    @Nullable
    private final StringValueResolver embeddedValueResolver;
    private final Converter<String, Duration> durationConverter;
    private final Logger logger = LoggerFactory.getLogger(SpringLockConfigurationExtractor.class);

    public SpringLockConfigurationExtractor(
        Duration defaultLockAtMostFor,
        Duration defaultLockAtLeastFor,
        @Nullable StringValueResolver embeddedValueResolver,
        Converter<String, Duration> durationConverter
    ) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
        this.durationConverter = requireNonNull(durationConverter);
        this.embeddedValueResolver = embeddedValueResolver;
    }


    @Override
    public Optional<LockConfiguration> getLockConfiguration(Runnable task) {
        if (task instanceof ScheduledMethodRunnable scheduledMethodRunnable) {
            return getLockConfiguration(scheduledMethodRunnable.getTarget(), scheduledMethodRunnable.getMethod(), new Object[] {});
        } else {
            logger.debug("Unknown task type " + task);
        }
        return Optional.empty();
    }

    @Override
    public Optional<LockConfiguration> getLockConfiguration(Object target, Method method, Object[] parameterValues) {
        AnnotationData annotation = findAnnotation(target, method);
        if (shouldLock(annotation)) {
            return Optional.of(getLockConfiguration(annotation, method, parameterValues));
        } else {
            return Optional.empty();
        }
    }

    private LockConfiguration getLockConfiguration(AnnotationData annotation, Method method, Object[] parameterValues) {
        return new LockConfiguration(
            ClockProvider.now(),
            getName(annotation, method, parameterValues),
            getLockAtMostFor(annotation),
            getLockAtLeastFor(annotation));
    }

    private String getName(AnnotationData annotation, Method method, Object[] parameterValues) {
        String name = parseSpEL(annotation.getName(), method, parameterValues);
        if (embeddedValueResolver != null) {
            return embeddedValueResolver.resolveStringValue(name);
        } else {
            return name;
        }
    }

    private String parseSpEL(String name, Method method, Object[] parameterValues) {
        return getEvaluationContext(method, parameterValues)
            .map(evaluationContext -> EXPRESSION_PARSER.parseExpression(name, PARSER_CONTEXT).getValue(evaluationContext, String.class))
            .orElse(name);
    }

    private Optional<EvaluationContext> getEvaluationContext(Method method, Object[] parameterValues) {
        if (method.getParameters().length > 0 && method.getParameters().length == parameterValues.length) {
            Parameter[] parameters = method.getParameters();
            EvaluationContext evaluationContext = SimpleEvaluationContext.forReadOnlyDataBinding().withInstanceMethods().build();
            for (int i = 0; i < parameters.length; i++) {
                evaluationContext.setVariable(parameters[i].getName(), parameterValues[i]);
            }
            return Optional.of(evaluationContext);
        } else {
            return Optional.empty();
        }
    }

    Duration getLockAtMostFor(AnnotationData annotation) {
        return getValue(
            annotation.getLockAtMostFor(),
            annotation.getLockAtMostForString(),
            this.defaultLockAtMostFor,
            "lockAtMostForString"
        );
    }

    Duration getLockAtLeastFor(AnnotationData annotation) {
        return getValue(
            annotation.getLockAtLeastFor(),
            annotation.getLockAtLeastForString(),
            this.defaultLockAtLeastFor,
            "lockAtLeastForString"
        );
    }

    private Duration getValue(long valueFromAnnotation, String stringValueFromAnnotation, Duration defaultValue, final String paramName) {
        if (valueFromAnnotation >= 0) {
            return Duration.of(valueFromAnnotation, MILLIS);
        } else if (StringUtils.hasText(stringValueFromAnnotation)) {
            if (embeddedValueResolver != null) {
                stringValueFromAnnotation = embeddedValueResolver.resolveStringValue(stringValueFromAnnotation);
            }
            try {
                Duration result = durationConverter.convert(stringValueFromAnnotation);
                if (result.isNegative()) {
                    throw new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation + "\" - cannot set negative duration");
                }
                return result;
            } catch (IllegalStateException nfe) {
                throw new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation + "\" - cannot parse into long nor duration");
            }
        } else {
            return defaultValue;
        }
    }

    @Nullable
    AnnotationData findAnnotation(Object target, Method method) {
        AnnotationData annotation = findAnnotation(method);
        if (annotation != null) {
            return annotation;
        } else {
            // Try to find annotation on proxied class
            Class<?> targetClass = AopUtils.getTargetClass(target);
            try {
                Method methodOnTarget = targetClass
                    .getMethod(method.getName(), method.getParameterTypes());
                return findAnnotation(methodOnTarget);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }
    }

    @Nullable
    private AnnotationData findAnnotation(Method method) {
        SchedulerLock annotation = AnnotatedElementUtils.getMergedAnnotation(method, SchedulerLock.class);
        if (annotation != null) {
            return new AnnotationData(annotation.name(), -1, annotation.lockAtMostFor(), -1, annotation.lockAtLeastFor());
        }
        return null;
    }

    private boolean shouldLock(@Nullable AnnotationData annotation) {
        return annotation != null;
    }

    static class AnnotationData {
        private final String name;
        private final long lockAtMostFor;
        private final String lockAtMostForString;
        private final long lockAtLeastFor;
        private final String lockAtLeastForString;

        private AnnotationData(String name, long lockAtMostFor, String lockAtMostForString, long lockAtLeastFor, String lockAtLeastForString) {
            this.name = name;
            this.lockAtMostFor = lockAtMostFor;
            this.lockAtMostForString = lockAtMostForString;
            this.lockAtLeastFor = lockAtLeastFor;
            this.lockAtLeastForString = lockAtLeastForString;
        }

        public String getName() {
            return name;
        }

        public long getLockAtMostFor() {
            return lockAtMostFor;
        }

        public String getLockAtMostForString() {
            return lockAtMostForString;
        }

        public long getLockAtLeastFor() {
            return lockAtLeastFor;
        }

        public String getLockAtLeastForString() {
            return lockAtLeastForString;
        }
    }
}


