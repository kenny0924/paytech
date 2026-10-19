package com.paytech.channel.connector.configuration;

import java.time.Clock;
import java.util.Objects;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

/** channel-connector 进程级时间源与标识生成器（渠道成本写入使用）。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChannelConnectorIdentifierProperties.class)
public class ChannelConnectorRuntimeConfiguration {

    /** 所有业务写入共用的 UTC 时间源。 */
    @Bean
    Clock channelConnectorClock() {
        return Clock.systemUTC();
    }

    /** 进程唯一的 Snowflake 生成器；缺少 Region/Worker 配置时启动失败。 */
    @Bean
    IdGenerator channelConnectorIdGenerator(ChannelConnectorIdentifierProperties properties) {
        return new SnowflakeIdGenerator(
            Objects.requireNonNull(properties.getRegionId(), "channel-connector.identifier.region-id"),
            Objects.requireNonNull(properties.getWorkerId(), "channel-connector.identifier.worker-id"));
    }
}
