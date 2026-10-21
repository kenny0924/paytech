package com.paytech.billing.app.configuration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Platform 币种快照的刷新装配
 * <li>刷新入口 Bean 始终存在；只有定时触发受 {@code billing.currency-snapshot.scheduling-enabled} 控制（自动测试关闭定时器，
 * 由测试经同一刷新入口显式刷新）。</li>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CurrencySnapshotProperties.class)
public class CurrencySnapshotConfiguration {

    /** 每个实例刷新自己的内存快照，使用进程内调度而非 XXL-Job（XXL 一次只触发一个实例）。 */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(name = "billing.currency-snapshot.scheduling-enabled", havingValue = "true",
        matchIfMissing = true)
    static class CurrencySnapshotSchedulingConfiguration {
    }
}
