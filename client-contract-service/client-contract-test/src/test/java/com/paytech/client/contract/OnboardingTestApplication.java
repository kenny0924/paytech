package com.paytech.client.contract;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 入驻 Mock 测试组合根：复用正式应用扫描，仅附加入驻测试组件与 Platform/Risk 测试 HTTP 服务。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.client.contract.test.mock.external.onboarding", "com.paytech.client.contract.test.mocker.onboarding",
        "com.paytech.client.contract.test.dal.onboarding"},
    baseUrlProperty = "cct.test.external.base-url")
@Import(ClientContractApplication.class)
public class OnboardingTestApplication {
}
