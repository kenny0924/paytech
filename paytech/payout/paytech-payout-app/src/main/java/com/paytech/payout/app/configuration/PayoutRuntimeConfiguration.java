package com.paytech.payout.app.configuration;

import java.time.Clock;
import java.util.Objects;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

/** payout-service 进程级时间源与标识生成器。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PayoutIdentifierProperties.class)
public class PayoutRuntimeConfiguration {

    /** 所有业务写入与冷却判定共用的 UTC 时间源。 */
    @Bean
    Clock payoutClock() {
        return Clock.systemUTC();
    }

    /** 进程唯一的 Snowflake 生成器；缺少 Region/Worker 配置时启动失败。 */
    @Bean
    IdGenerator payoutIdGenerator(PayoutIdentifierProperties properties) {
        return new SnowflakeIdGenerator(Objects.requireNonNull(properties.getRegionId(), "payout.identifier.region-id"),
            Objects.requireNonNull(properties.getWorkerId(), "payout.identifier.worker-id"));
    }
}
