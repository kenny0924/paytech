package com.paytech.gateway.routing;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/** Operations 浏览器 Origin 与传输边界的部署配置。 */
@ConfigurationProperties("gateway.operations")
public record OperationsRoutingProperties(String consoleOrigin, Duration responseTimeout, boolean testHttpEnabled) {

    private static final Duration MAX_RESPONSE_TIMEOUT = Duration.ofSeconds(10);

    @ConstructorBinding
    public OperationsRoutingProperties {
        validateConsoleOrigin(consoleOrigin, testHttpEnabled);
        validateResponseTimeout(responseTimeout);
    }

    public OperationsRoutingProperties(String consoleOrigin, Duration responseTimeout) {
        this(consoleOrigin, responseTimeout, false);
    }

    private static void validateConsoleOrigin(String value, boolean testHttpEnabled) {
        try {
            URI origin = URI.create(value);
            boolean allowedScheme = testHttpEnabled ? isLocalTestOrigin(origin) : "https".equals(origin.getScheme());
            if (!value.equals(origin.toString()) || origin.getHost() == null || !allowedScheme
                || origin.getRawPath() == null || !origin.getRawPath().isEmpty() || origin.getRawQuery() != null
                || origin.getRawFragment() != null || origin.getUserInfo() != null) {
                throw new IllegalArgumentException("无效的 Operations Console Origin");
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                "gateway.operations.console-origin 必须是精确 HTTPS Origin，或显式 test HTTP localhost Origin", exception);
        }
    }

    private static boolean isLocalTestOrigin(URI origin) {
        return "http".equals(origin.getScheme()) && "localhost".equals(origin.getHost()) && origin.getPort() > 0
            && origin.getPort() <= 65535 && ("localhost:" + origin.getPort()).equals(origin.getRawAuthority());
    }

    private static void validateResponseTimeout(Duration value) {
        if (value == null || value.isZero() || value.isNegative() || value.compareTo(MAX_RESPONSE_TIMEOUT) > 0) {
            throw new IllegalArgumentException("gateway.operations.response-timeout 必须大于 0 且不超过 10 秒");
        }
    }

}
