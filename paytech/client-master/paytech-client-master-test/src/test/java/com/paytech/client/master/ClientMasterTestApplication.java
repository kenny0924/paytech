package com.paytech.client.master;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * client-master Mock 测试组合根：复用正式应用扫描，仅附加测试组件、Platform/Risk/commercial 测试 HTTP 服务与分区队列基线。
 * <li>{@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 client-master.compliance.p0..p3 与 DLQ。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.client.master.test.mock.external.onboarding", "com.paytech.client.master.test.mocker.onboarding",
        "com.paytech.client.master.test.dal.onboarding", "com.paytech.client.master.test.mock.external.commercial",
        "com.paytech.client.master.test.mocker.commercial", "com.paytech.client.master.test.mocker.checkout",
        "com.paytech.client.master.test.dal.checkout", "com.paytech.client.master.test.clear"},
    baseUrlProperty = "client-master.test.external.base-url")
@Import({ClientMasterApplication.class, PartitionQueueBaselineConfiguration.class})
public class ClientMasterTestApplication {
}
