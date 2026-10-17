package com.paytech.consumer.configuration;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

/** consumer 进程级时间与应用 ID 能力的装配。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConsumerIdentifierProperties.class)
public class ConsumerRuntimeConfiguration {

    /**
     * 为业务处理提供唯一 UTC 时间源。
     *
     * @return 进程级 UTC Clock
     */
    @Bean
    Clock consumerClock() {
        return Clock.systemUTC();
    }

    /**
     * 根据实例节点配置创建唯一的 Foundation ID 生成器；Customer 与补偿任务共用，避免同 Worker 序列碰撞。
     *
     * @param properties 实例部署分配的 Snowflake 节点配置
     * @return ID 生成器
     */
    @Bean
    IdGenerator consumerIdGenerator(ConsumerIdentifierProperties properties) {
        return new SnowflakeIdGenerator(properties.getRegionId(), properties.getWorkerId());
    }
}
