package com.paytech.compliance.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * 外部 HTTP 替身：channel-connector 服务渠道执行与 client-master 资料读取在独立 Loopback Mock 服务上应答
 * <li>地址写入 compliance.test.external.base-url，由 application-mock.yml 的 compliance.outbound.test-base-url 引用；
 * 真实出站客户端经该地址调用，保留生产转换与错误处理。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.compliance.test.mock.external", "com.paytech.compliance.test.mocker.external"},
    baseUrlProperty = "compliance.test.external.base-url")
public class ComplianceTestMockConfiguration {
}
