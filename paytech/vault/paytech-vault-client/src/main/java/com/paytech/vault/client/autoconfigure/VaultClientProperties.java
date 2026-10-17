package com.paytech.vault.client.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 客户端启动配置；服务发现和测试直连是互斥的目标模式。
 *
 * @param callerService
 *            固定调用服务名；未配置时读取 spring.application.name
 * @param serviceId
 *            发现目标服务名；未配置时为 vault-service
 * @param connectTimeout
 *            必填连接超时，大于零且不超过 30 秒
 * @param readTimeout
 *            必填读取超时，大于零且不超过 30 秒；正亚毫秒值向上取整
 * @param baseUrl
 *            仅 test/unittest Profile 可显式配置的私有 HTTP 根地址，不得同时显式配置 service-id
 * @param adminEnabled
 *            是否创建 VaultAdminClient；默认 false，只有 Vault 的管理调用方（Operations BFF）开启
 */
@ConfigurationProperties("vault.client")
public record VaultClientProperties(String callerService, String serviceId, Duration connectTimeout,
                                    Duration readTimeout, String baseUrl, boolean adminEnabled) {
    @Override
    public String toString() {
        return "VaultClientProperties[redacted]";
    }
}
