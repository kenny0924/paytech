package com.paytech.billing.app.configuration;

import java.time.Clock;
import java.util.Objects;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

/** 计费服务（billing-service）进程级时间源与标识生成器。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BillingIdentifierProperties.class)
public class BillingRuntimeConfiguration {

    /** 所有业务写入共用的 UTC 时间源。 */
    @Bean
    Clock billingClock() {
        return Clock.systemUTC();
    }

    /** 进程唯一的 Snowflake 生成器；缺少 Region/Worker 配置时启动失败。 */
    @Bean
    IdGenerator billingIdGenerator(BillingIdentifierProperties properties) {
        return new SnowflakeIdGenerator(Objects.requireNonNull(properties.getRegionId(), "billing.identifier.region-id"),
            Objects.requireNonNull(properties.getWorkerId(), "billing.identifier.worker-id"));
    }
}
