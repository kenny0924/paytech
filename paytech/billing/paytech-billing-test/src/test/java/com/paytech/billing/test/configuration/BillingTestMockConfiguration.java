package com.paytech.billing.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * 独立 Loopback Mock HTTP 服务承载 platform-support 参考数据（币种）与 commercial、channel-connector、reconciliation、ledger 查询合同；
 * 生产装配由正式应用完成。参考数据的 Mock API 与 Mocker 来自 paytech-platform-support-reference-data-client-test-support。
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.billing.test.mock.external", "com.paytech.billing.test.mocker.external",
    "com.paytech.platform.support.referencedata.client.test"},
    baseUrlProperty = "billing.test.external.base-url")
public class BillingTestMockConfiguration {
}
