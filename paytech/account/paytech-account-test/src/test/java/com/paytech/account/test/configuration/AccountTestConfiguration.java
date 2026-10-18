package com.paytech.account.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 只增加测试 Mapper 与独立的 ledger / billing HTTP Mock，生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.account.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.account.test.mock.external", "com.paytech.account.test.mocker.external"},
    baseUrlProperty = "account.test.external.base-url")
public class AccountTestConfiguration {
}
