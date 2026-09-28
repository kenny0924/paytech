package com.paytech.platform.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.platform.support.test.configuration.LocalTestConfiguration;

/**
 * Platform Support Mock 测试组合根：复用正式启动类的组件扫描与自动配置，只附加测试组件。
 *
 * <p>独立 HTTP Mock 实现 Vault 正式接口，业务侧仍使用正式 VaultClient。
 * 本地测试组件和 Mapper 由 LocalTestConfiguration 扫描；邮件 Provider 仅在测试中提供适配器。
 */
@com.paytech.foundation.testsupport.api.EnableMockHttpServer(basePackages = "com.paytech.platform.support.test.notification.http",
    baseUrlProperty = "test.notification.vault-url")
@TestConfiguration(proxyBeanMethods = false)
@Import({PlatformSupportApplication.class, LocalTestConfiguration.class})
public class PlatformSupportTestApplication {
}
