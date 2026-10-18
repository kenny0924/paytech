package com.paytech.gateway.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Operations Access Token 校验所需的部署级可信配置。 */
@ConfigurationProperties("gateway.operations.security")
public record OperationsSecurityProperties(String issuer, String audience) {
    public OperationsSecurityProperties {
        if (isBlank(issuer) || isBlank(audience)) {
            throw new IllegalArgumentException("gateway.operations.security.issuer 和 audience 不可为空");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
