package com.paytech.ledger.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 独立 Loopback Mock HTTP 服务承载 account（过账进度、批次）与 billing（事实详情、发布进度）合同；生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.ledger.test.mock.external", "com.paytech.ledger.test.mocker.external"},
    baseUrlProperty = "ledger.test.external.base-url")
public class LedgerTestMockConfiguration {
}
