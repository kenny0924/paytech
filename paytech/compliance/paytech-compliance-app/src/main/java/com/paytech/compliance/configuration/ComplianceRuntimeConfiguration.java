package com.paytech.compliance.configuration;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.paytech.compliance.domainservice.port.ComplianceIdentifierGenerator;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.id.SnowflakeIdGenerator;

/**
 * compliance 进程级基础能力装配：独立的 Snowflake worker（compliance.identifier.*，design/01 §3–4）与 UTC 时间源。
 */
@Configuration(proxyBeanMethods = false)
public class ComplianceRuntimeConfiguration {

    /**
     * 为所有业务写入提供唯一的 UTC 时间源。
     *
     * @return 进程级 UTC Clock
     */
    @Bean
    Clock complianceClock() {
        return Clock.systemUTC();
    }

    /**
     * 进程唯一的 Foundation Snowflake 生成器；补偿任务登记与合规标识端口共用。
     *
     * @param regionId 部署分配的 Region 标识
     * @param workerId 部署分配的 Worker 标识（实例清单分配，不得与 risk-decision 相同）
     * @return 进程级标识生成器
     */
    @Bean
    IdGenerator complianceIdGenerator(
        @Value("${compliance.identifier.region-id}") long regionId,
        @Value("${compliance.identifier.worker-id}") long workerId
    ) {
        return new SnowflakeIdGenerator(regionId, workerId);
    }

    /**
     * 将 Foundation Snowflake 实现隔离在合规模块定义的窄端口之后。
     *
     * @param complianceIdGenerator 进程唯一的 Snowflake 生成器
     * @return 合规标识生成端口
     */
    @Bean
    ComplianceIdentifierGenerator complianceIdentifierGenerator(IdGenerator complianceIdGenerator) {
        return complianceIdGenerator::nextId;
    }
}
