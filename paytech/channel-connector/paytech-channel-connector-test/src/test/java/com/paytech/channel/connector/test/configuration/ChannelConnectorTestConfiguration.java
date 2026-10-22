package com.paytech.channel.connector.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.paytech.channel.connector.test.mocker.VaultTransactionObserver;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 只增加测试 Mapper 与独立的 Vault / card / 外部渠道 HTTP Mock；生产装配（含 Fixture 复用的 Snowflake 生成器）由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.channel.connector.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.channel.connector.test.mocker", "com.paytech.channel.connector.test.mock",
    "com.paytech.channel.connector.test.probe"},
    baseUrlProperty = "test.mock-http.base-url")
public class ChannelConnectorTestConfiguration {
    /** 定制第三方 HTTP Builder 的只读观察点，保留真实客户端全部协议行为。 */
    @Bean
    RestClientCustomizer observeVaultTransaction(VaultTransactionObserver observer) {
        return builder -> builder.requestInitializer(request -> observer.observe());
    }
}
