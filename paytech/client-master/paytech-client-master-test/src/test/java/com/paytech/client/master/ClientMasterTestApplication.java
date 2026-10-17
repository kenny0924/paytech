package com.paytech.client.master;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 入驻 Mock 测试组合根：复用正式应用扫描，仅附加入驻测试组件与 Platform/Risk 测试 HTTP 服务。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.client.master.test.mock.external.onboarding", "com.paytech.client.master.test.mocker.onboarding",
        "com.paytech.client.master.test.dal.onboarding"},
    baseUrlProperty = "cct.test.external.base-url")
@Import(ClientMasterApplication.class)
public class ClientMasterTestApplication {
}
