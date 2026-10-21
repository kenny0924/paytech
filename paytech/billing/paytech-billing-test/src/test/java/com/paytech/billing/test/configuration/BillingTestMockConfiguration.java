package com.paytech.billing.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 独立 Loopback Mock HTTP 服务承载 platform-support 币种规则查询合同；生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.billing.test.mock.external", "com.paytech.billing.test.mocker.external"},
    baseUrlProperty = "billing.test.external.base-url")
public class BillingTestMockConfiguration {
}
