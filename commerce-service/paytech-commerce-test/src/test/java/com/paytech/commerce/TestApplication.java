package com.paytech.commerce;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.commerce.test.dal.CheckoutCreationTestMapper;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** Mock 测试组合根：复用正式应用扫描，仅附加测试组件与外部 HTTP Mock。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.commerce.test.mock.external", "com.paytech.commerce.test.mocker",
        "com.paytech.commerce.test.checker", "com.paytech.commerce.test.checkout"},
    baseUrlProperty = "commerce.test.external.base-url")
@MapperScan(basePackageClasses = CheckoutCreationTestMapper.class, annotationClass = Mapper.class)
@Import(CommerceApplication.class)
public class TestApplication {
}
