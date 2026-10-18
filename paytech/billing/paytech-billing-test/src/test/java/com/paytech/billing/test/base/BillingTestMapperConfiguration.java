package com.paytech.billing.test.base;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

/** 生产 {@code @MapperScan} 只扫描 core 的 Mapper 包；测试专用 Mapper 由本配置单独登记。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.billing.test.dal")
public class BillingTestMapperConfiguration {
}
