package com.paytech.payment.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 只增加测试 Mapper 与独立的 channel-connector / Risk / Vault HTTP Mock，生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.payment.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.payment.test.mocker", "com.paytech.payment.test.mock"},
    baseUrlProperty = "test.mock-http.base-url")
public class PaymentTestConfiguration {
    /** 定制第三方 HTTP Builder 的只读观察点，保留真实客户端全部协议行为。 */
    @org.springframework.context.annotation.Bean
    org.springframework.boot.restclient.RestClientCustomizer observeVaultTransaction(
        com.paytech.payment.test.mocker.VaultTransactionObserver observer) {
        return builder -> builder.requestInitializer(request -> observer.observe());
    }

}
