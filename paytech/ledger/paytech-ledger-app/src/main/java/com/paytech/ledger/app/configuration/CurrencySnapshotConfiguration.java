package com.paytech.ledger.app.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Platform 币种规则快照的刷新装配
 * <li>刷新入口 {@code CurrencySnapshotRefreshScheduler} 始终装配；只有开启调度时其 {@code @Scheduled} 才生效。</li>
 * <li>每个实例刷新自己的内存，所以用进程内调度而非 XXL-Job（XXL 一次只触发一个实例）。</li>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CurrencySnapshotProperties.class)
public class CurrencySnapshotConfiguration {

    /** 默认开启；自动测试设为 false，由用例显式调用刷新入口。 */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "ledger.currency-snapshot.scheduling-enabled", havingValue = "true",
        matchIfMissing = true)
    static class SchedulingConfiguration {
    }
}
