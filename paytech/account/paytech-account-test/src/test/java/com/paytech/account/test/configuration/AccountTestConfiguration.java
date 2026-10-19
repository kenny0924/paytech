package com.paytech.account.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

/** 只增加测试 Mapper；account 没有出站 HTTP 依赖，生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.account.test.dal")
public class AccountTestConfiguration {
}
