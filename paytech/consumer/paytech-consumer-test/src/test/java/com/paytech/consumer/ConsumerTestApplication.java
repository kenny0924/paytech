package com.paytech.consumer;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.consumer.test.dal.CustomerTestMapper;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** Mock 测试组合根：复用正式应用扫描，仅附加测试组件与外部 HTTP Mock（Payment 已存卡、client-master 存卡开关）。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.consumer.test.mock.external", "com.paytech.consumer.test.mocker"},
    baseUrlProperty = "consumer.test.external.base-url")
@MapperScan(basePackageClasses = CustomerTestMapper.class, annotationClass = Mapper.class)
@Import(ConsumerApplication.class)
public class ConsumerTestApplication {
}
