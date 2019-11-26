/**
 * Copyright 2009-2019 the original author or authors.
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
package net.javacrumbs.shedlock.spring.internal;

import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockConfigurationExtractor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAmount;
import java.util.Optional;

import static java.time.Instant.now;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.Objects.requireNonNull;

public class SpringLockConfigurationExtractor implements LockConfigurationExtractor {
    private final TemporalAmount defaultLockAtMostFor;
    private final TemporalAmount defaultLockAtLeastFor;
    private final StringValueResolver embeddedValueResolver;
    private final Logger logger = LoggerFactory.getLogger(SpringLockConfigurationExtractor.class);

    public SpringLockConfigurationExtractor(@NotNull TemporalAmount defaultLockAtMostFor, @NotNull TemporalAmount defaultLockAtLeastFor, @Nullable StringValueResolver embeddedValueResolver) {
        this.defaultLockAtMostFor = requireNonNull(defaultLockAtMostFor);
        this.defaultLockAtLeastFor = requireNonNull(defaultLockAtLeastFor);
        this.embeddedValueResolver = embeddedValueResolver;
    }


    @Override
    @NotNull
    public Optional<LockConfiguration> getLockConfiguration(@NotNull Runnable task) {
        if (task instanceof ScheduledMethodRunnable) {
            ScheduledMethodRunnable scheduledMethodRunnable = (ScheduledMethodRunnable) task;
            return getLockConfiguration(scheduledMethodRunnable.getTarget(), scheduledMethodRunnable.getMethod());
        } else {
            logger.debug("Unknown task type " + task);
        }
        return Optional.empty();
    }

    public Optional<LockConfiguration> getLockConfiguration(Object target, Method method) {
        AnnotationData annotation = findAnnotation(target, method);
        if (shouldLock(annotation)) {
            return Optional.of(getLockConfiguration(annotation));
        } else {
            return Optional.empty();
        }
    }

    private LockConfiguration getLockConfiguration(AnnotationData annotation) {
        Instant now = now();
        return new LockConfiguration(
            getName(annotation),
            now.plus(getLockAtMostFor(annotation)),
            now.plus(getLockAtLeastFor(annotation)));
    }

    private String getName(AnnotationData annotation) {
        if (embeddedValueResolver != null) {
            return embeddedValueResolver.resolveStringValue(annotation.getName());
        } else {
            return annotation.getName();
        }
    }

    TemporalAmount getLockAtMostFor(AnnotationData annotation) {
        return getValue(
            annotation.getLockAtMostFor(),
            annotation.getLockAtMostForString(),
            this.defaultLockAtMostFor,
            "lockAtMostForString"
        );
    }

    TemporalAmount getLockAtLeastFor(AnnotationData annotation) {
        return getValue(
            annotation.getLockAtLeastFor(),
            annotation.getLockAtLeastForString(),
            this.defaultLockAtLeastFor,
            "lockAtLeastForString"
        );
    }

    private TemporalAmount getValue(long valueFromAnnotation, String stringValueFromAnnotation, TemporalAmount defaultValue, final String paramName) {
        if (valueFromAnnotation >= 0) {
            return Duration.of(valueFromAnnotation, MILLIS);
        } else if (StringUtils.hasText(stringValueFromAnnotation)) {
            if (embeddedValueResolver != null) {
                stringValueFromAnnotation = embeddedValueResolver.resolveStringValue(stringValueFromAnnotation);
            }
            try {
                return Duration.of(Long.parseLong(stringValueFromAnnotation), MILLIS);
            } catch (NumberFormatException nfe) {
                try {
                    return Duration.parse(stringValueFromAnnotation);
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Invalid " + paramName + " value \"" + stringValueFromAnnotation + "\" - cannot parse into long nor duration");
                }
            }
        } else {
            return defaultValue;
        }
    }

    AnnotationData findAnnotation(Object target, Method method) {
        AnnotationData annotation = findAnnotation(method);
        if (annotation != null) {
            return annotation;
        } else {
            // Try to find annotation on proxied class
            Class<?> targetClass = AopUtils.getTargetClass(target);
            if (targetClass != null) {
                try {
                    Method methodOnTarget = targetClass
                        .getMethod(method.getName(), method.getParameterTypes());
                    return findAnnotation(methodOnTarget);
                } catch (NoSuchMethodException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
    }

    private AnnotationData findAnnotation(Method method) {
        net.javacrumbs.shedlock.core.SchedulerLock annotation = AnnotatedElementUtils.getMergedAnnotation(method, net.javacrumbs.shedlock.core.SchedulerLock.class);
        if (annotation != null) {
            return new AnnotationData(annotation.name(), annotation.lockAtMostFor(), annotation.lockAtMostForString(), annotation.lockAtLeastFor(), annotation.lockAtLeastForString());
        }
        SchedulerLock annotation2 = AnnotatedElementUtils.getMergedAnnotation(method, SchedulerLock.class);
        if (annotation2 != null) {
            return new AnnotationData(annotation2.name(), -1, annotation2.lockAtMostFor(), -1, annotation2.lockAtLeastFor());
        }
        return null;
    }

    private boolean shouldLock(AnnotationData annotation) {
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


