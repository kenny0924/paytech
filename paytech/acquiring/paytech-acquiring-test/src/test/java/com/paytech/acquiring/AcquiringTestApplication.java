package com.paytech.acquiring;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

import com.paytech.acquiring.test.dal.CheckoutCreationTestMapper;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** Mock 测试组合根：复用正式应用扫描，仅附加测试组件与外部 HTTP Mock。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(
    basePackages = {"com.paytech.acquiring.test.mock.external", "com.paytech.acquiring.test.mocker",
        "com.paytech.acquiring.test.checker", "com.paytech.acquiring.test.checkout"},
    baseUrlProperty = "acquiring.test.external.base-url")
@MapperScan(basePackageClasses = CheckoutCreationTestMapper.class, annotationClass = Mapper.class)
@Import(AcquiringApplication.class)
public class AcquiringTestApplication {
}
