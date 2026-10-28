package com.paytech.ledger.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * 独立 Loopback Mock HTTP 服务承载 account（过账进度、批次）、billing（事实详情、发布进度）与 platform-support 参考数据（币种）合同；
 * 生产装配由正式应用完成。参考数据的 Mock API 与 Mocker 来自 paytech-platform-support-reference-data-client-test-support。
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.ledger.test.mock.external", "com.paytech.ledger.test.mocker.external",
    "com.paytech.platform.support.referencedata.client.test"},
    baseUrlProperty = "ledger.test.external.base-url")
public class LedgerTestMockConfiguration {
}
