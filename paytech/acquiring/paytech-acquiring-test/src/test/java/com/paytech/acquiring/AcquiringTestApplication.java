package com.paytech.acquiring;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.acquiring.test.dal.CheckoutCreationTestMapper;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * Mock 测试组合根：复用正式应用扫描，仅附加测试组件、外部 HTTP Mock 与分区队列基线。
 * <li>{@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 acquiring.payment.p0..p3 与 DLQ。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.acquiring.test.mock.external", "com.paytech.acquiring.test.mocker",
        "com.paytech.acquiring.test.checker", "com.paytech.acquiring.test.checkout"},
    baseUrlProperty = "acquiring.test.external.base-url")
@MapperScan(basePackageClasses = CheckoutCreationTestMapper.class, annotationClass = Mapper.class)
@Import({AcquiringApplication.class, PartitionQueueBaselineConfiguration.class})
public class AcquiringTestApplication {
}
