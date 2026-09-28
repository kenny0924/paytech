package com.paytech.foundation.compensation.core.execution;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.compensation.api.CompensationEventHandler;
import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;
import com.paytech.foundation.compensation.core.error.CompensationErrorCode;

/**
 * 应用启动时创建的、按事件类型索引的不可变 Handler 注册表。
 *
 * @author Codex
 */
public final class CompensationHandlerRegistry {

    private final Map<String, ConfiguredHandler> handlers;

    public CompensationHandlerRegistry(Collection<CompensationEventHandler> handlers, Duration leaseDuration) {
        this.handlers = indexHandlers(handlers, requirePositiveLease(leaseDuration));
    }

    public void validateRegistration(String eventType) {
        requireHandler(eventType);
    }

    public CompensationEventHandler requireHandler(String eventType) {
        ConfiguredHandler configuredHandler = handlers.get(requireEventType(eventType));
        if (configuredHandler == null) {
            throw configurationError("compensation Handler is not configured: " + eventType);
        }
        return configuredHandler.handler();
    }

    public CompensationExecutionPolicy policy(String eventType) {
        ConfiguredHandler configuredHandler = handlers.get(requireEventType(eventType));
        if (configuredHandler == null) {
            throw configurationError("compensation Handler is not configured: " + eventType);
        }
        return configuredHandler.policy();
    }

    public String controlledEventType(String eventType) {
        if (eventType == null || !handlers.containsKey(eventType)) {
            return "unconfigured";
        }
        return eventType;
    }

    private Map<String, ConfiguredHandler> indexHandlers(Collection<CompensationEventHandler> candidates,
        Duration leaseDuration) {
        Map<String, ConfiguredHandler> indexedHandlers = new HashMap<>();
        if (candidates == null) {
            return Map.of();
        }
        for (CompensationEventHandler handler : candidates) {
            CompensationEventHandler requiredHandler = Objects.requireNonNull(handler, "Handler must not be null");
            String eventType = requireEventType(requiredHandler.eventType());
            CompensationExecutionPolicy policy =
                Objects.requireNonNull(requiredHandler.policy(), "execution policy must not be null: " + eventType);
            if (policy.timeout().compareTo(leaseDuration) >= 0) {
                throw configurationError("Handler timeout must be shorter than Lease: " + eventType);
            }
            ConfiguredHandler duplicate =
                indexedHandlers.putIfAbsent(eventType, new ConfiguredHandler(requiredHandler, policy));
            if (duplicate != null) {
                throw configurationError("duplicate compensation Handler: " + eventType);
            }
        }
        return Map.copyOf(indexedHandlers);
    }

    private String requireEventType(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            throw configurationError("eventType must not be blank");
        }
        String requiredType = eventType.trim();
        if (requiredType.length() > 64) {
            throw configurationError("eventType exceeds 64 characters: " + requiredType);
        }
        return requiredType;
    }

    private Duration requirePositiveLease(Duration leaseDuration) {
        Duration requiredDuration = Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        if (requiredDuration.isZero() || requiredDuration.isNegative()) {
            throw configurationError("leaseDuration must be positive");
        }
        return requiredDuration;
    }

    private FailException configurationError(String message) {
        return new FailException(CompensationErrorCode.CONFIGURATION_INVALID, message);
    }

    private record ConfiguredHandler(CompensationEventHandler handler, CompensationExecutionPolicy policy) {
    }
}
