package com.paytech.reconciliation.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * 只增加测试 Mapper 与独立的 channel-connector / Vault HTTP Mock；生产装配由正式应用完成
 * <li>EnableMockHttpServer 扫描 test 包下全部 {@code @TestComponent}：Mock Controller 只进入独立 Mock HTTP 服务，其余 Mocker、Probe、
 * 清理组件注册为宿主 Bean；配置类不被扫描。</li>
 * <li>Mock 服务的随机地址写入 test.mock-http.base-url，application-mock.yml 的 simple discovery 把 channel-connector-service 与
 * vault-service 指向它，真实出站代理与 LoadBalancer 保持原样。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.reconciliation.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.reconciliation.test.mock", "com.paytech.reconciliation.test.mocker",
    "com.paytech.reconciliation.test.probe", "com.paytech.reconciliation.test.clear"},
    baseUrlProperty = "test.mock-http.base-url")
public class ReconciliationTestConfiguration {
}
