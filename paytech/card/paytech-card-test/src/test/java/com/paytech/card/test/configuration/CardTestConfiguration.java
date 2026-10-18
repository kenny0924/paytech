package com.paytech.card.test.configuration;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.paytech.card.test.mocker.VaultTransactionObserver;
import com.paytech.foundation.messaging.testkit.PartitionQueueBaselineConfiguration;
import com.paytech.foundation.testsupport.api.EnableMockHttpServer;

/**
 * 只增加测试 Mapper、独立的 Vault HTTP Mock 与分区队列基线，生产装配由正式应用完成。
 * <li>{@link PartitionQueueBaselineConfiguration} 在监听容器启动前清空 card.payment.p0..p3 与 DLQ（其他模块测试的残留消息）。</li>
 */
@TestConfiguration(proxyBeanMethods = false)
@MapperScan("com.paytech.card.test.dal")
@EnableMockHttpServer(basePackages = {"com.paytech.card.test.mocker", "com.paytech.card.test.mock"},
    baseUrlProperty = "test.mock-http.base-url")
@Import(PartitionQueueBaselineConfiguration.class)
public class CardTestConfiguration {
    /** 定制第三方 HTTP Builder 的只读观察点（Vault 客户端），保留真实客户端全部协议行为。 */
    @Bean
    RestClientCustomizer observeVaultTransaction(VaultTransactionObserver observer) {
        return builder -> builder.requestInitializer(request -> observer.observe());
    }
}
