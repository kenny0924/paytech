package com.paytech.ledger.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 独立 Loopback Mock HTTP 服务承载 account（consistency）与 billing（pending）合同；生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.ledger.test.mock.external", "com.paytech.ledger.test.mocker.external"},
    baseUrlProperty = "ledger.test.external.base-url")
public class LedgerTestMockConfiguration {
}
