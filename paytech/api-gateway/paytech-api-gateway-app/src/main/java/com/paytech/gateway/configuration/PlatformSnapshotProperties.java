package com.paytech.gateway.configuration;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Gateway 私有 PLT-I20 控制面连接配置。 */
@ConfigurationProperties("gateway.platform-snapshot")
public record PlatformSnapshotProperties(URI baseUri, String serviceId, boolean directUriEnabled,
    Duration connectTimeout, Duration responseTimeout, int maxResponseBytes, Duration refreshInterval) {

    public static final int MAX_RESPONSE_BYTES = 1_048_576;
    public static final int MAX_JSON_NESTING_DEPTH = 32;
    private static final Duration MAX_CONTROL_TIMEOUT = Duration.ofSeconds(10);
    private static final String DEFAULT_SERVICE_ID = "platform-support-service";

    public PlatformSnapshotProperties {
        serviceId = normalize(serviceId);
        if (baseUri == null && serviceId == null) {
            serviceId = DEFAULT_SERVICE_ID;
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(2) : connectTimeout;
        responseTimeout = responseTimeout == null ? Duration.ofSeconds(2) : responseTimeout;
        refreshInterval = refreshInterval == null ? Duration.ofSeconds(5) : refreshInterval;
        maxResponseBytes = maxResponseBytes == 0 ? MAX_RESPONSE_BYTES : maxResponseBytes;
        if ((baseUri != null && !directUriEnabled) || (baseUri != null && serviceId != null)
            || (baseUri == null && serviceId == null) || (baseUri != null && !validBaseUri(baseUri))
            || (serviceId != null && !serviceId.matches("[a-z][a-z0-9-]{0,62}")) || connectTimeout.isNegative()
            || connectTimeout.isZero() || responseTimeout.isNegative() || responseTimeout.isZero()
            || connectTimeout.compareTo(MAX_CONTROL_TIMEOUT) > 0 || responseTimeout.compareTo(MAX_CONTROL_TIMEOUT) > 0
            || refreshInterval.isNegative() || refreshInterval.isZero() || maxResponseBytes <= 0
            || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Gateway Platform Snapshot 私有 HTTP 配置非法");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean validBaseUri(URI value) {
        return "http".equalsIgnoreCase(value.getScheme()) && value.getHost() != null && value.getRawPath() != null
            && value.getRawPath().isEmpty() && value.getRawUserInfo() == null && value.getRawQuery() == null
            && value.getRawFragment() == null;
    }

    public boolean discoveryEnabled() {
        return serviceId != null;
    }
}
