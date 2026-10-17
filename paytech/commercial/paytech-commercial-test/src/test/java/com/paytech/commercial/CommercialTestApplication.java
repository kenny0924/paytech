package com.paytech.commercial;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** commercial Mock 测试组合根：复用正式应用扫描，仅附加测试组件与 client-master 测试 HTTP 服务。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.commercial.test.mock.external.clientmaster", "com.paytech.commercial.test.mocker",
        "com.paytech.commercial.test.dal", "com.paytech.commercial.test.clear"},
    baseUrlProperty = "commercial.test.external.base-url")
@Import(CommercialApplication.class)
public class CommercialTestApplication {
}
