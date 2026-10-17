package com.paytech.gateway.guard.redis;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Redis Guard 的显式环境命名空间。 */
@ConfigurationProperties("gateway.operations.guard.redis")
public record GuardRedisProperties(String namespace) {
    private static final int MAX_NAMESPACE_LENGTH = 128;
    private static final Pattern NAMESPACE_PATTERN =
        Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*(?::[a-z0-9]+(?:-[a-z0-9]+)*)*");
    private static final Pattern EXPLICIT_VERSION_PATTERN = Pattern.compile("v[0-9]+");

    public GuardRedisProperties {
        if (namespace == null || namespace.length() > MAX_NAMESPACE_LENGTH
            || !NAMESPACE_PATTERN.matcher(namespace).matches() || embedsGuardVersion(namespace)) {
            throw new IllegalArgumentException("Redis Guard Namespace 格式无效");
        }
    }

    private static boolean embedsGuardVersion(String namespace) {
        String[] segments = namespace.split(":", -1);
        for (int index = 0; index + 1 < segments.length; index++) {
            if (segments[index].equals("guard") && EXPLICIT_VERSION_PATTERN.matcher(segments[index + 1]).matches()) {
                return true;
            }
        }
        return false;
    }
}
