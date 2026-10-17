package com.paytech.gateway.configuration;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

/** 私有 PLT-I03 调用预算；direct URI 只能用于唯一 auto Profile 的 Loopback Simulator。 */
@ConfigurationProperties("gateway.platform-credential")
public record PlatformCredentialValidationProperties(URI baseUri, String serviceId, boolean directUriEnabled,
    Duration connectTimeout, Duration responseTimeout, int maxResponseBytes) {
    public static final int MAX_RESPONSE_BYTES = 16_384;
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(10);

    public PlatformCredentialValidationProperties {
        serviceId = serviceId == null || serviceId.isBlank() ? null : serviceId.trim();
        if (baseUri == null && serviceId == null) {
            serviceId = "platform-support-service";
        }
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(1) : connectTimeout;
        responseTimeout = responseTimeout == null ? Duration.ofSeconds(2) : responseTimeout;
        maxResponseBytes = maxResponseBytes == 0 ? MAX_RESPONSE_BYTES : maxResponseBytes;
        if ((baseUri != null && (!directUriEnabled || serviceId != null || !validBaseUri(baseUri)))
            || (directUriEnabled && baseUri == null)
            || (serviceId != null && !serviceId.matches("[a-z][a-z0-9-]{0,62}"))
            || !validTimeout(connectTimeout) || !validTimeout(responseTimeout)
            || maxResponseBytes <= 0 || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Gateway Platform Credential 私有 HTTP 配置非法");
        }
    }

    private static boolean validTimeout(Duration value) {
        return value.toMillis() >= 1 && value.compareTo(MAX_TIMEOUT) <= 0;
    }

    private static boolean validBaseUri(URI value) {
        return "http".equals(value.getScheme()) && "127.0.0.1".equals(value.getHost())
            && value.getPort() > 0 && value.getPort() <= 65535 && "".equals(value.getRawPath())
            && value.getRawUserInfo() == null && value.getRawQuery() == null && value.getRawFragment() == null;
    }

    public void validateEnvironment(Environment environment) {
        if (directUriEnabled && !Arrays.equals(environment.getActiveProfiles(), new String[] {"auto"})) {
            throw new IllegalStateException("PLT-I03 direct URI 仅允许唯一 auto Profile");
        }
    }

    public boolean discoveryEnabled() {
        return serviceId != null;
    }
}
