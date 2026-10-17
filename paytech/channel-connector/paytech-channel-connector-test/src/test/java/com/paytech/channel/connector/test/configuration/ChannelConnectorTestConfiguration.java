package com.paytech.channel.connector.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.paytech.channel.connector.test.mocker.VaultTransactionObserver;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/** 只增加测试 Mapper、Fixture ID 与独立的 Vault / Platform HTTP Mock，生产装配由正式应用完成。 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.channel.connector.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.channel.connector.test.mocker", "com.paytech.channel.connector.test.mock"},
    baseUrlProperty = "test.mock-http.base-url")
public class ChannelConnectorTestConfiguration {
    /** 定制第三方 HTTP Builder 的只读观察点，保留真实客户端全部协议行为。 */
    @Bean
    RestClientCustomizer observeVaultTransaction(VaultTransactionObserver observer) {
        return builder -> builder.requestInitializer(request -> observer.observe());
    }

    /** Fixture ID 使用正式 Snowflake 实现；生产代码只读渠道配置，不生成 ID。 */
    @Bean
    IdGenerator channelConnectorFixtureIds() {
        return new SnowflakeIdGenerator(31L, 29L);
    }
}
