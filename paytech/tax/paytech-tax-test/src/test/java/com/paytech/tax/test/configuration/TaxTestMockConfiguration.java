package com.paytech.tax.test.configuration;

import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 独立 Loopback Mock HTTP 服务承载 Platform 内部文件接口与对象存储直传地址；生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@EnableMockHttpServer(basePackages = {"com.paytech.tax.test.mock.external", "com.paytech.tax.test.mocker.external"},
    baseUrlProperty = "tax.test.external.base-url")
public class TaxTestMockConfiguration {
}
