package com.paytech.client.master;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** client-master Mock 测试组合根：复用正式应用扫描，仅附加测试组件与 Platform/Risk/commercial 测试 HTTP 服务。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.client.master.test.mock.external.onboarding", "com.paytech.client.master.test.mocker.onboarding",
        "com.paytech.client.master.test.dal.onboarding", "com.paytech.client.master.test.mock.external.commercial",
        "com.paytech.client.master.test.mocker.commercial", "com.paytech.client.master.test.mocker.checkout",
        "com.paytech.client.master.test.dal.checkout", "com.paytech.client.master.test.clear"},
    baseUrlProperty = "client-master.test.external.base-url")
@Import(ClientMasterApplication.class)
public class ClientMasterTestApplication {
}
