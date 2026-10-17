package com.paytech.gateway.configuration;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Platform Support Operations JWKS 私有连接配置。 */
@ConfigurationProperties("gateway.platform-jwks")
public record PlatformJwksProperties(URI baseUri, String serviceId, boolean directUriEnabled) {

    private static final String DEFAULT_SERVICE_ID = "platform-support-service";

    public PlatformJwksProperties {
        serviceId = normalize(serviceId);
        if (baseUri == null && serviceId == null) {
            serviceId = DEFAULT_SERVICE_ID;
        }
        if ((baseUri != null && !directUriEnabled) || (baseUri != null && serviceId != null)
            || (baseUri == null && serviceId == null) || (baseUri != null && !validBaseUri(baseUri))
            || (serviceId != null && !serviceId.matches("[a-z][a-z0-9-]{0,62}"))) {
            throw new IllegalArgumentException("Gateway Platform JWKS 私有 HTTP 配置非法");
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
