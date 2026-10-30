package com.paytech.commercial;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * commercial Mock 测试组合根：复用正式应用扫描，仅附加测试组件、client-master / compliance / channel-connector 测试 HTTP 服务与分区队列基线。
 * <li>{@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 commercial.compliance.p0..p3 与 DLQ（QualificationChanged 消费者）。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.commercial.test.mock.external.clientmaster",
        "com.paytech.commercial.test.mock.external.compliance",
        "com.paytech.commercial.test.mock.external.channelconnector", "com.paytech.commercial.test.mocker",
        "com.paytech.commercial.test.dal", "com.paytech.commercial.test.clear",
        "com.paytech.commercial.test.checker.write"},
    baseUrlProperty = "commercial.test.external.base-url")
@Import({CommercialApplication.class, PartitionQueueBaselineConfiguration.class})
public class CommercialTestApplication {
}
